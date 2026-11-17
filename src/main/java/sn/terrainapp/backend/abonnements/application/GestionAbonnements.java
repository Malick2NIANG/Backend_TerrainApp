package sn.terrainapp.backend.abonnements.application;

import static sn.terrainapp.backend.commun.audit.Libelles.fcfa;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.terrainapp.backend.abonnements.domaine.Abonnement;
import sn.terrainapp.backend.abonnements.domaine.Frequence;
import sn.terrainapp.backend.abonnements.domaine.ModeReglement;
import sn.terrainapp.backend.abonnements.infrastructure.AbonnementRepository;
import sn.terrainapp.backend.commun.audit.JournalActions;
import sn.terrainapp.backend.commun.audit.JournalActions.Domaine;
import sn.terrainapp.backend.commun.diffusion.ChangementPublic;
import sn.terrainapp.backend.commun.erreurs.ConflitException;
import sn.terrainapp.backend.commun.erreurs.RequeteInvalideException;
import sn.terrainapp.backend.commun.erreurs.RessourceIntrouvableException;
import sn.terrainapp.backend.commun.securite.UtilisateurCourant;
import sn.terrainapp.backend.commun.telephone.Telephone;
import sn.terrainapp.backend.commun.temps.Horloge;
import sn.terrainapp.backend.paiements.application.PaiementService;
import sn.terrainapp.backend.paiements.domaine.MoyenPaiement;
import sn.terrainapp.backend.reservations.application.JetonSuivi;
import sn.terrainapp.backend.reservations.application.ReservationVue;
import sn.terrainapp.backend.reservations.domaine.Canal;
import sn.terrainapp.backend.reservations.domaine.Reservation;
import sn.terrainapp.backend.reservations.domaine.StatutReservation;
import sn.terrainapp.backend.reservations.infrastructure.ReservationRepository;
import sn.terrainapp.backend.terrains.application.Creneaux;
import sn.terrainapp.backend.terrains.domaine.Terrain;
import sn.terrainapp.backend.terrains.infrastructure.BlocageRepository;
import sn.terrainapp.backend.terrains.infrastructure.TerrainRepository;

/**
 * Abonnements au comptoir : apercu des seances (libres ou non), creation
 * des seances libres en reservations confirmees, encaissement seance par
 * seance, resiliation (les seances a venir sont annulees).
 */
@Service
public class GestionAbonnements {

    public static final int MAX_HEBDO = 52;
    public static final int MAX_MENSUEL = 12;
    private static final EnumSet<StatutReservation> OCCUPANTS = EnumSet.of(StatutReservation.EN_ATTENTE_PAIEMENT,
            StatutReservation.CONFIRMEE, StatutReservation.HONOREE);

    /** Une seance prevue : disponible, ou la raison pour laquelle elle sera sautee. */
    public record SeanceApercu(Instant debut, Instant fin, long prix, boolean disponible, String motif) {
    }

    public record Apercu(List<SeanceApercu> seances, int disponibles, long total) {
    }

    private final AbonnementRepository abonnements;
    private final ReservationRepository reservations;
    private final TerrainRepository terrains;
    private final BlocageRepository blocages;
    private final Creneaux creneaux;
    private final PaiementService paiements;
    private final ApplicationEventPublisher publication;
    private final JournalActions journal;
    private final Clock horloge;

    public GestionAbonnements(AbonnementRepository abonnements, ReservationRepository reservations,
                              TerrainRepository terrains, BlocageRepository blocages, Creneaux creneaux,
                              PaiementService paiements, ApplicationEventPublisher publication, JournalActions journal,
                              Clock horloge) {
        this.abonnements = abonnements;
        this.reservations = reservations;
        this.terrains = terrains;
        this.blocages = blocages;
        this.creneaux = creneaux;
        this.paiements = paiements;
        this.publication = publication;
        this.journal = journal;
        this.horloge = horloge;
    }

    /** Dates des seances : chaque semaine, ou le meme rang du jour chaque mois (5e semaine = dernier). */
    static List<Instant> dates(Instant premiere, Frequence frequence, int nombre) {
        ZonedDateTime z = premiere.atZone(Horloge.DAKAR);
        List<Instant> dates = new ArrayList<>();
        DayOfWeek jour = z.getDayOfWeek();
        int rang = (z.getDayOfMonth() - 1) / 7 + 1;
        for (int k = 0; k < nombre; k++) {
            if (frequence == Frequence.HEBDOMADAIRE) {
                dates.add(z.plusWeeks(k).toInstant());
            } else {
                YearMonth mois = YearMonth.from(z).plusMonths(k);
                LocalDate d = rang >= 5
                        ? mois.atEndOfMonth().with(TemporalAdjusters.previousOrSame(jour))
                        : mois.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(rang, jour));
                dates.add(ZonedDateTime.of(d, z.toLocalTime(), Horloge.DAKAR).toInstant());
            }
        }
        return dates;
    }

    @Transactional(readOnly = true)
    public Apercu apercu(long terrainId, Instant premiere, Frequence frequence, int nombre) {
        Terrain terrain = terrains.findById(terrainId).orElseThrow(() -> new RequeteInvalideException(
                "TERRAIN_INDISPONIBLE", "Terrain introuvable.", "terrainId"));
        return calculer(terrain, premiere, frequence, nombre);
    }

    private Apercu calculer(Terrain terrain, Instant premiere, Frequence frequence, int nombre) {
        if (frequence == null) {
            throw new RequeteInvalideException("FREQUENCE_REQUISE", "Choisissez une fréquence.", "frequence");
        }
        int max = frequence == Frequence.HEBDOMADAIRE ? MAX_HEBDO : MAX_MENSUEL;
        if (nombre < 2 || nombre > max) {
            throw new RequeteInvalideException("NOMBRE_SEANCES", "Entre 2 et " + max + " séances.", "nombreSeances");
        }
        Instant maintenant = Instant.now(horloge);
        List<SeanceApercu> seances = new ArrayList<>();
        for (Instant debut : dates(premiere, frequence, nombre)) {
            var creneau = creneaux.commencantA(terrain, debut);
            if (creneau.isEmpty()) {
                seances.add(new SeanceApercu(debut, null, 0, false, "Hors des horaires"));
                continue;
            }
            Instant fin = creneau.get().fin();
            long prix = creneau.get().prix();
            String motif = null;
            if (!debut.isAfter(maintenant)) {
                motif = "Déjà passé";
            } else if (blocages.entre(debut, fin).stream().anyMatch(b -> b.chevauche(terrain.getId(), debut, fin))) {
                motif = "Terrain fermé";
            } else if (reservations.chevauchement(terrain.getId(), debut, fin, OCCUPANTS)) {
                motif = "Déjà réservé";
            }
            seances.add(new SeanceApercu(debut, fin, prix, motif == null, motif));
        }
        int disponibles = (int) seances.stream().filter(SeanceApercu::disponible).count();
        long total = seances.stream().filter(SeanceApercu::disponible).mapToLong(SeanceApercu::prix).sum();
        return new Apercu(seances, disponibles, total);
    }

    @Transactional
    public AbonnementVue creer(SaisieAbonnement s) {
        String telephone = Telephone.normaliser(s.clientTelephone()).orElseThrow(() -> new RequeteInvalideException(
                "TELEPHONE_INVALIDE", "Indiquez un numéro sénégalais à 9 chiffres.", "clientTelephone"));
        if (s.reglement() == ModeReglement.AVANCE && s.moyenPaiement() == null) {
            throw new RequeteInvalideException("MOYEN_PAIEMENT_REQUIS", "Indiquez comment le client a payé.", "moyenPaiement");
        }
        Terrain terrain = terrains.verrouiller(s.terrainId())
                .filter(t -> t.isActif() && t.getSport().isActif())
                .orElseThrow(() -> new RequeteInvalideException("TERRAIN_INDISPONIBLE",
                        "Ce terrain n'est pas réservable.", "terrainId"));
        Apercu apercu = calculer(terrain, s.premiereSeance(), s.frequence(), s.nombreSeances());
        if (apercu.disponibles() == 0) {
            throw new ConflitException("AUCUNE_SEANCE_LIBRE", "Aucune de ces séances n'est libre.");
        }
        if (s.remise() > apercu.total()) {
            throw new RequeteInvalideException("REMISE_TROP_ELEVEE", "La remise dépasse le total.", "remise");
        }
        String par = UtilisateurCourant.identifiant().orElse(null);
        Abonnement a = abonnements.save(new Abonnement(terrain, s.clientNom().trim(), telephone, s.frequence(),
                s.premiereSeance(), s.nombreSeances(), s.reglement(), s.remise(), par));

        boolean leJourJ = s.reglement() == ModeReglement.SEANCE;
        MoyenPaiement moyen = leJourJ ? MoyenPaiement.ESPECES : s.moyenPaiement();
        List<SeanceApercu> libres = apercu.seances().stream().filter(SeanceApercu::disponible).toList();
        long remiseRestante = s.remise();
        for (int i = 0; i < libres.size(); i++) {
            SeanceApercu seance = libres.get(i);
            long part = i == libres.size() - 1 ? remiseRestante : s.remise() * seance.prix() / apercu.total();
            remiseRestante -= part;
            Reservation r = new Reservation(nouvelleReference(), terrain, seance.debut(), seance.fin(),
                    seance.prix() - part, Canal.COMPTOIR, moyen, a.getClientNom(), telephone, null,
                    JetonSuivi.codeTicket(), par);
            r.rattacherAbonnement(a.getId(), leJourJ);
            reservations.save(r);
            if (!leJourJ) {
                paiements.encaisserAuComptoir(r);
            }
        }
        publication.publishEvent(new ChangementPublic(ChangementPublic.Sujet.DISPONIBILITES));
        journal.enregistrer(Domaine.RESERVATION, "ABONNEMENT", String.valueOf(a.getId()), "a créé l'abonnement de "
                + a.getClientNom() + " sur " + terrain.getNom() + " (" + libres.size() + " séances, "
                + fcfa(apercu.total() - s.remise()) + (leJourJ ? ", payable à chaque séance)" : ", payé d'avance)"));
        return vues(List.of(a)).get(0);
    }

    @Transactional(readOnly = true)
    public List<AbonnementVue> tous() {
        return vues(abonnements.findAllByOrderByStatutAscCreeLeDesc());
    }

    @Transactional(readOnly = true)
    public AbonnementVue un(long id) {
        return vues(List.of(abonnements.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Abonnement introuvable.")))).get(0);
    }

    /** Resiliation : les seances a venir sont annulees (creneaux liberes) ; les seances passees restent. */
    @Transactional
    public AbonnementVue resilier(long id) {
        Abonnement a = abonnements.verrouiller(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Abonnement introuvable."));
        Instant maintenant = Instant.now(horloge);
        a.resilier(maintenant);
        String par = UtilisateurCourant.identifiant().orElse("systeme");
        int annulees = 0;
        for (Reservation r : reservations.findByAbonnementIdInOrderByDebut(List.of(id))) {
            if (r.getStatut() == StatutReservation.CONFIRMEE && r.getDebut().isAfter(maintenant)) {
                r.annuler(par, maintenant);
                annulees++;
            }
        }
        publication.publishEvent(new ChangementPublic(ChangementPublic.Sujet.DISPONIBILITES));
        journal.enregistrer(Domaine.RESERVATION, "ABONNEMENT_RESILIE", String.valueOf(id), "a résilié l'abonnement de "
                + a.getClientNom() + " (" + annulees + " séance(s) à venir annulée(s))");
        return vues(List.of(a)).get(0);
    }

    /** Encaissement au comptoir d'une seance payable le jour J. */
    @Transactional
    public ReservationVue encaisser(long reservationId, MoyenPaiement moyen) {
        if (moyen == null) {
            throw new RequeteInvalideException("MOYEN_PAIEMENT_REQUIS", "Indiquez comment le client a payé.", "moyenPaiement");
        }
        Reservation r = reservations.verrouiller(reservationId)
                .orElseThrow(() -> new RessourceIntrouvableException("Réservation introuvable."));
        r.encaisser(moyen, Instant.now(horloge));
        paiements.encaisserAuComptoir(r);
        journal.enregistrer(Domaine.RESERVATION, "ENCAISSEMENT", r.getReference(), "a encaissé la séance "
                + r.getReference() + " (" + fcfa(r.getMontant()) + ", " + moyen.code() + ")");
        return ReservationVue.pourPersonnel(r);
    }

    private List<AbonnementVue> vues(List<Abonnement> liste) {
        if (liste.isEmpty()) {
            return List.of();
        }
        Instant maintenant = Instant.now(horloge);
        Map<Long, List<Reservation>> parAbonnement = reservations
                .findByAbonnementIdInOrderByDebut(liste.stream().map(Abonnement::getId).toList()).stream()
                .collect(Collectors.groupingBy(Reservation::getAbonnementId));
        return liste.stream().map(a -> {
            List<Reservation> seances = parAbonnement.getOrDefault(a.getId(), List.of());
            List<Reservation> valides = seances.stream().filter(r -> r.getStatut() != StatutReservation.ANNULEE).toList();
            long total = valides.stream().mapToLong(Reservation::getMontant).sum();
            int jouees = (int) valides.stream().filter(r -> r.getStatut() == StatutReservation.HONOREE
                    || !r.getFin().isAfter(maintenant)).count();
            List<Reservation> aVenir = valides.stream().filter(r -> r.getStatut() == StatutReservation.CONFIRMEE
                    && r.getFin().isAfter(maintenant)).toList();
            List<Reservation> aEncaisser = valides.stream().filter(r -> r.getPayeeLe() == null).toList();
            return new AbonnementVue(a.getId(), a.getTerrain().getId(), a.getTerrain().getNom(),
                    a.getTerrain().getSport().getNom(), a.getClientNom(), a.getClientTelephone(), a.getFrequence(),
                    a.getPremiereSeance(), a.getNombreSeances(), a.getReglement(), a.getRemise(), a.getStatut(),
                    a.getResilieLe(), a.getCreeLe(), total, jouees, aVenir.size(), aEncaisser.size(),
                    aEncaisser.stream().mapToLong(Reservation::getMontant).sum(),
                    aVenir.isEmpty() ? null : aVenir.get(0).getDebut(),
                    seances.stream().map(ReservationVue::pourPersonnel).toList());
        }).toList();
    }

    private String nouvelleReference() {
        for (int i = 0; i < 10; i++) {
            String ref = JetonSuivi.reference();
            if (!reservations.existsByReference(ref)) {
                return ref;
            }
        }
        throw new IllegalStateException("Impossible de generer une reference unique");
    }
}
