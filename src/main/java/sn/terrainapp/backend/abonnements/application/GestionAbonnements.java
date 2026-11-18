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

    /** Regle de recurrence : frequence, nombre de seances, jours de la semaine ou dates choisies. */
    public record Regle(Frequence frequence, int nombre, java.util.Set<Integer> jours, List<LocalDate> dates) {
    }

    /**
     * Dates des seances, a l'heure de la 1re : chaque semaine (aux jours
     * choisis, sinon celui de la 1re seance), le meme rang du jour chaque mois
     * (5e semaine = dernier), ou les dates choisies (avec la 1re, sans doublon).
     */
    static List<Instant> dates(Instant premiere, Regle regle) {
        ZonedDateTime z = premiere.atZone(Horloge.DAKAR);
        List<Instant> dates = new ArrayList<>();
        DayOfWeek jour = z.getDayOfWeek();
        if (regle.frequence() == Frequence.PERSONNALISEE) {
            java.util.TreeSet<LocalDate> choisies = new java.util.TreeSet<>(regle.dates() == null ? List.of() : regle.dates());
            choisies.add(z.toLocalDate());
            choisies.forEach(d -> dates.add(ZonedDateTime.of(d, z.toLocalTime(), Horloge.DAKAR).toInstant()));
            return dates;
        }
        if (regle.frequence() == Frequence.HEBDOMADAIRE) {
            java.util.Set<Integer> jours = regle.jours() == null || regle.jours().isEmpty()
                    ? java.util.Set.of(jour.getValue()) : regle.jours();
            for (LocalDate d = z.toLocalDate(); dates.size() < regle.nombre(); d = d.plusDays(1)) {
                if (jours.contains(d.getDayOfWeek().getValue())) {
                    dates.add(ZonedDateTime.of(d, z.toLocalTime(), Horloge.DAKAR).toInstant());
                }
            }
            return dates;
        }
        int rang = (z.getDayOfMonth() - 1) / 7 + 1;
        for (int k = 0; k < regle.nombre(); k++) {
            YearMonth mois = YearMonth.from(z).plusMonths(k);
            LocalDate d = rang >= 5
                    ? mois.atEndOfMonth().with(TemporalAdjusters.previousOrSame(jour))
                    : mois.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(rang, jour));
            dates.add(ZonedDateTime.of(d, z.toLocalTime(), Horloge.DAKAR).toInstant());
        }
        return dates;
    }

    @Transactional(readOnly = true)
    public Apercu apercu(long terrainId, Instant premiere, Regle regle) {
        Terrain terrain = terrains.findById(terrainId).orElseThrow(() -> new RequeteInvalideException(
                "TERRAIN_INDISPONIBLE", "Terrain introuvable.", "terrainId"));
        return calculer(terrain, premiere, regle);
    }

    /** Prolongation : une seule seance de plus suffit. */
    private Apercu calculerSansMinimum(Terrain terrain, Instant premiere, Regle regle) {
        return evaluer(terrain, premiere, regle);
    }

    private Apercu calculer(Terrain terrain, Instant premiere, Regle regle) {
        if (regle.frequence() == null) {
            throw new RequeteInvalideException("FREQUENCE_REQUISE", "Choisissez une fréquence.", "frequence");
        }
        if (regle.frequence() == Frequence.PERSONNALISEE) {
            int n = regle.dates() == null ? 0 : regle.dates().size();
            if (n < 1 || n > MAX_HEBDO) {
                throw new RequeteInvalideException("DATES_REQUISES", "Choisissez entre 2 et " + MAX_HEBDO + " dates.", "dates");
            }
        } else {
            int max = regle.frequence() == Frequence.HEBDOMADAIRE ? MAX_HEBDO : MAX_MENSUEL;
            if (regle.nombre() < 2 || regle.nombre() > max) {
                throw new RequeteInvalideException("NOMBRE_SEANCES", "Entre 2 et " + max + " séances.", "nombreSeances");
            }
        }
        return evaluer(terrain, premiere, regle);
    }

    private Apercu evaluer(Terrain terrain, Instant premiere, Regle regle) {
        Instant maintenant = Instant.now(horloge);
        List<SeanceApercu> seances = new ArrayList<>();
        for (Instant debut : dates(premiere, regle)) {
            var creneau = creneaux.periode(terrain, debut, terrain.getDureeCreneauMinutes());
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
        Regle regle = new Regle(s.frequence(), s.nombreSeances(), s.jours(), s.dates());
        Apercu apercu = calculer(terrain, s.premiereSeance(), regle);
        if (apercu.disponibles() == 0) {
            throw new ConflitException("AUCUNE_SEANCE_LIBRE", "Aucune de ces séances n'est libre.");
        }
        if (s.remise() > apercu.total()) {
            throw new RequeteInvalideException("REMISE_TROP_ELEVEE", "La remise dépasse le total.", "remise");
        }
        String par = UtilisateurCourant.identifiant().orElse(null);
        Abonnement a = abonnements.save(new Abonnement(terrain, s.clientNom().trim(), telephone, s.frequence(),
                s.premiereSeance(), apercu.seances().size(), s.reglement(), s.remise(), par));
        if (s.frequence() == Frequence.HEBDOMADAIRE) {
            a.definirJours(s.jours());
        }

        boolean leJourJ = s.reglement() == ModeReglement.SEANCE;
        List<SeanceApercu> libres = creerSeances(a, terrain, apercu, s.moyenPaiement(), s.remise(), par);
        publication.publishEvent(new ChangementPublic(ChangementPublic.Sujet.DISPONIBILITES));
        journal.enregistrer(Domaine.RESERVATION, "ABONNEMENT", String.valueOf(a.getId()), "a créé l'abonnement de "
                + a.getClientNom() + " sur " + terrain.getNom() + " (" + libres.size() + " séances, "
                + fcfa(apercu.total() - s.remise()) + (leJourJ ? ", payable à chaque séance)" : ", payé d'avance)"));
        return vues(List.of(a)).get(0);
    }

    /** Seances libres de l'apercu -> reservations confirmees (payees d'avance, ou a encaisser le jour J). */
    private List<SeanceApercu> creerSeances(Abonnement a, Terrain terrain, Apercu apercu, MoyenPaiement moyenAvance,
                                            long remise, String par) {
        boolean leJourJ = a.getReglement() == ModeReglement.SEANCE;
        MoyenPaiement moyen = leJourJ ? MoyenPaiement.ESPECES : moyenAvance;
        List<SeanceApercu> libres = apercu.seances().stream().filter(SeanceApercu::disponible).toList();
        long remiseRestante = remise;
        for (int i = 0; i < libres.size(); i++) {
            SeanceApercu seance = libres.get(i);
            long part = i == libres.size() - 1 ? remiseRestante : remise * seance.prix() / apercu.total();
            remiseRestante -= part;
            Reservation r = new Reservation(nouvelleReference(), terrain, seance.debut(), seance.fin(),
                    seance.prix() - part, Canal.COMPTOIR, moyen, a.getClientNom(), a.getClientTelephone(), null,
                    JetonSuivi.codeTicket(), par);
            r.rattacherAbonnement(a.getId(), leJourJ);
            reservations.save(r);
            if (!leJourJ) {
                paiements.encaisserAuComptoir(r);
            }
        }
        return libres;
    }

    /** Correction du client : l'abonnement et ses seances a venir. */
    @Transactional
    public AbonnementVue modifierClient(long id, String nom, String saisieTelephone) {
        Abonnement a = actif(id);
        String telephone = Telephone.normaliser(saisieTelephone).orElseThrow(() -> new RequeteInvalideException(
                "TELEPHONE_INVALIDE", "Indiquez un numéro sénégalais à 9 chiffres.", "clientTelephone"));
        if (nom == null || nom.trim().length() < 2 || nom.trim().length() > 80) {
            throw new RequeteInvalideException("NOM_REQUIS", "Indiquez le nom du client.", "clientNom");
        }
        a.changerClient(nom.trim(), telephone);
        Instant maintenant = Instant.now(horloge);
        for (Reservation r : reservations.findByAbonnementIdInOrderByDebut(List.of(id))) {
            if (r.getDebut().isAfter(maintenant) && r.getStatut() == StatutReservation.CONFIRMEE) {
                r.changerClient(a.getClientNom(), telephone);
            }
        }
        journal.enregistrer(Domaine.RESERVATION, "ABONNEMENT_MODIFIE", String.valueOf(id), "a modifié le client de l'abonnement "
                + a.getClientNom());
        return vues(List.of(a)).get(0);
    }

    /**
     * Prolonger : n seances de plus, a la suite de la derniere (meme regle,
     * meme heure, meme terrain). Dates au choix : les nouvelles dates.
     */
    @Transactional
    public AbonnementVue prolonger(long id, int nombre, List<LocalDate> nouvellesDates, MoyenPaiement moyen) {
        Abonnement a = actif(id);
        if (a.getReglement() == ModeReglement.AVANCE && moyen == null) {
            throw new RequeteInvalideException("MOYEN_PAIEMENT_REQUIS", "Indiquez comment le client a payé.", "moyenPaiement");
        }
        Terrain terrain = terrains.verrouiller(a.getTerrain().getId())
                .orElseThrow(() -> new RequeteInvalideException("TERRAIN_INDISPONIBLE", "Terrain introuvable.", "terrainId"));
        List<Reservation> seances = reservations.findByAbonnementIdInOrderByDebut(List.of(id));
        ZonedDateTime derniere = (seances.isEmpty() ? a.getPremiereSeance() : seances.get(seances.size() - 1).getDebut())
                .atZone(Horloge.DAKAR);
        Instant suivante;
        Regle regle;
        if (a.getFrequence() == Frequence.PERSONNALISEE) {
            if (nouvellesDates == null || nouvellesDates.isEmpty()) {
                throw new RequeteInvalideException("DATES_REQUISES", "Choisissez au moins une date.", "dates");
            }
            List<LocalDate> triees = nouvellesDates.stream().distinct().sorted().toList();
            suivante = ZonedDateTime.of(triees.get(0), derniere.toLocalTime(), Horloge.DAKAR).toInstant();
            regle = new Regle(Frequence.PERSONNALISEE, triees.size(), null, triees);
        } else {
            if (nombre < 1 || nombre > MAX_HEBDO) {
                throw new RequeteInvalideException("NOMBRE_SEANCES", "Entre 1 et " + MAX_HEBDO + " séances.", "nombreSeances");
            }
            java.util.Set<Integer> jours = a.getJours().isEmpty()
                    ? java.util.Set.of(a.getPremiereSeance().atZone(Horloge.DAKAR).getDayOfWeek().getValue())
                    : java.util.Set.copyOf(a.getJours());
            if (a.getFrequence() == Frequence.HEBDOMADAIRE) {
                LocalDate d = derniere.toLocalDate().plusDays(1);
                while (!jours.contains(d.getDayOfWeek().getValue())) {
                    d = d.plusDays(1);
                }
                suivante = ZonedDateTime.of(d, derniere.toLocalTime(), Horloge.DAKAR).toInstant();
            } else {
                // Meme rang du jour que la 1re seance, le mois suivant la derniere.
                ZonedDateTime p = a.getPremiereSeance().atZone(Horloge.DAKAR);
                int rang = (p.getDayOfMonth() - 1) / 7 + 1;
                YearMonth mois = YearMonth.from(derniere).plusMonths(1);
                LocalDate d = rang >= 5 ? mois.atEndOfMonth().with(TemporalAdjusters.previousOrSame(p.getDayOfWeek()))
                        : mois.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(rang, p.getDayOfWeek()));
                suivante = ZonedDateTime.of(d, p.toLocalTime(), Horloge.DAKAR).toInstant();
            }
            regle = new Regle(a.getFrequence(), nombre, jours, null);
        }
        Apercu apercu = calculerSansMinimum(terrain, suivante, regle);
        if (apercu.disponibles() == 0) {
            throw new ConflitException("AUCUNE_SEANCE_LIBRE", "Aucune de ces séances n'est libre.");
        }
        String par = UtilisateurCourant.identifiant().orElse(null);
        List<SeanceApercu> libres = creerSeances(a, terrain, apercu, moyen, 0, par);
        a.ajouterSeances(apercu.seances().size());
        publication.publishEvent(new ChangementPublic(ChangementPublic.Sujet.DISPONIBILITES));
        journal.enregistrer(Domaine.RESERVATION, "ABONNEMENT_PROLONGE", String.valueOf(id), "a prolongé l'abonnement de "
                + a.getClientNom() + " (" + libres.size() + " séance(s), " + fcfa(apercu.total()) + ")");
        return vues(List.of(a)).get(0);
    }

    /**
     * Supprimer un abonnement cree par erreur : seulement si aucune seance n'a
     * ete jouee ni payee. Ses seances sont annulees, il disparait de la liste.
     */
    @Transactional
    public void supprimer(long id) {
        Abonnement a = abonnements.verrouiller(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Abonnement introuvable."));
        Instant maintenant = Instant.now(horloge);
        List<Reservation> seances = reservations.findByAbonnementIdInOrderByDebut(List.of(id));
        boolean entame = seances.stream().anyMatch(r -> r.getStatut() != StatutReservation.ANNULEE
                && (r.getPayeeLe() != null || !r.getDebut().isAfter(maintenant)));
        if (entame) {
            throw new ConflitException("ABONNEMENT_ENTAME",
                    "Des séances sont payées ou déjà passées : résiliez l'abonnement plutôt que de le supprimer.");
        }
        String par = UtilisateurCourant.identifiant().orElse("systeme");
        for (Reservation r : seances) {
            if (r.getStatut() == StatutReservation.CONFIRMEE) {
                r.annuler(par, maintenant, "Abonnement supprimé");
            }
        }
        a.supprimer(maintenant);
        publication.publishEvent(new ChangementPublic(ChangementPublic.Sujet.DISPONIBILITES));
        journal.enregistrer(Domaine.RESERVATION, "ABONNEMENT_SUPPRIME", String.valueOf(id), "a supprimé l'abonnement de "
                + a.getClientNom() + " (créé par erreur)");
    }

    private Abonnement actif(long id) {
        Abonnement a = abonnements.verrouiller(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Abonnement introuvable."));
        if (a.getStatut() != sn.terrainapp.backend.abonnements.domaine.StatutAbonnement.ACTIF) {
            throw new ConflitException("ABONNEMENT_TERMINE", "Cet abonnement n'est plus actif.");
        }
        return a;
    }

    @Transactional(readOnly = true)
    public List<AbonnementVue> tous() {
        return vues(abonnements.findByStatutNotOrderByStatutAscCreeLeDesc(sn.terrainapp.backend.abonnements.domaine.StatutAbonnement.SUPPRIME));
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
    public ReservationVue encaisser(long reservationId, MoyenPaiement moyen, Long especesRecues) {
        if (moyen == null) {
            throw new RequeteInvalideException("MOYEN_PAIEMENT_REQUIS", "Indiquez comment le client a payé.", "moyenPaiement");
        }
        Reservation r = reservations.verrouiller(reservationId)
                .orElseThrow(() -> new RessourceIntrouvableException("Réservation introuvable."));
        r.encaisser(moyen, Instant.now(horloge));
        r.noterEspeces(especesRecues);
        paiements.encaisserAuComptoir(r);
        journal.enregistrer(Domaine.RESERVATION, "ENCAISSEMENT", r.getReference(), "a encaissé la séance "
                + r.getReference() + " (" + fcfa(r.getMontant()) + ", " + moyen.code() + ")");
        return ReservationVue.pourClient(r);
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
                    seances.stream().map(ReservationVue::pourPersonnel).toList(), a.getJours());
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
