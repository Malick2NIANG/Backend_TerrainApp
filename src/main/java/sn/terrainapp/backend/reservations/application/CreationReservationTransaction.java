package sn.terrainapp.backend.reservations.application;

import static sn.terrainapp.backend.commun.audit.Libelles.fcfa;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;

import sn.terrainapp.backend.commun.audit.JournalActions;
import sn.terrainapp.backend.commun.audit.JournalActions.Domaine;
import sn.terrainapp.backend.commun.diffusion.ChangementPublic;
import sn.terrainapp.backend.commun.erreurs.ConflitException;
import sn.terrainapp.backend.commun.erreurs.RequeteInvalideException;
import sn.terrainapp.backend.commun.idempotence.Idempotence;
import sn.terrainapp.backend.commun.securite.UtilisateurCourant;
import sn.terrainapp.backend.commun.telephone.Telephone;
import sn.terrainapp.backend.commun.temps.Horloge;
import sn.terrainapp.backend.paiements.application.PaiementService;
import sn.terrainapp.backend.paiements.application.PaiementService.IssuePaiement;
import sn.terrainapp.backend.paiements.domaine.MoyenPaiement;
import sn.terrainapp.backend.reservations.domaine.Canal;
import sn.terrainapp.backend.reservations.domaine.Reservation;
import sn.terrainapp.backend.reservations.domaine.StatutReservation;
import sn.terrainapp.backend.reservations.infrastructure.ReservationRepository;
import sn.terrainapp.backend.tempsreel.EvenementSortant;
import sn.terrainapp.backend.tempsreel.EvenementSortantRepository;
import sn.terrainapp.backend.terrains.application.Catalogue;
import sn.terrainapp.backend.terrains.application.Creneaux;
import sn.terrainapp.backend.terrains.domaine.Terrain;
import sn.terrainapp.backend.terrains.infrastructure.BlocageRepository;
import sn.terrainapp.backend.terrains.infrastructure.TerrainRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Creation d'une reservation, en une transaction : verrou sur le terrain
 * (deux joueurs sur le meme creneau sont servis l'un apres l'autre, le
 * second recoit 409), verifications, reservation, paiement, evenements.
 */
@Service
public class CreationReservationTransaction {

    private static final EnumSet<StatutReservation> OCCUPANTS = EnumSet.of(StatutReservation.EN_ATTENTE_PAIEMENT,
            StatutReservation.CONFIRMEE, StatutReservation.HONOREE);
    private static final EnumSet<MoyenPaiement> EN_LIGNE = EnumSet.of(MoyenPaiement.WAVE, MoyenPaiement.ORANGE_MONEY, MoyenPaiement.CARTE,
            MoyenPaiement.MIXX_YAS);

    /** Reponse de creation : la vue, le jeton de suivi (APP, donne une seule fois) et le paiement. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReservationCreee(ReservationVue reservation, String jetonSuivi, Paiement paiement) {
    }

    public record Paiement(String reference, String redirectUrl, boolean simule) {
    }

    private final TerrainRepository terrains;
    private final ReservationRepository reservations;
    private final BlocageRepository blocages;
    private final Creneaux creneaux;
    private final PaiementService paiements;
    private final EvenementSortantRepository evenements;
    private final ApplicationEventPublisher publication;
    private final JournalActions journal;
    private final Idempotence idempotence;
    private final ObjectMapper json;
    private final Clock horloge;

    public CreationReservationTransaction(TerrainRepository terrains, ReservationRepository reservations,
                                          BlocageRepository blocages, Creneaux creneaux, PaiementService paiements,
                                          EvenementSortantRepository evenements, ApplicationEventPublisher publication,
                                          JournalActions journal, Idempotence idempotence, ObjectMapper json,
                                          Clock horloge) {
        this.terrains = terrains;
        this.reservations = reservations;
        this.blocages = blocages;
        this.creneaux = creneaux;
        this.paiements = paiements;
        this.evenements = evenements;
        this.publication = publication;
        this.journal = journal;
        this.idempotence = idempotence;
        this.json = json;
        this.horloge = horloge;
    }

    @Transactional
    public String executer(SaisieReservation s, Canal canal, String cleIdempotence) {
        Instant maintenant = Instant.now(horloge);
        if (canal == Canal.APP && !EN_LIGNE.contains(s.moyenPaiement())) {
            throw new RequeteInvalideException("MOYEN_PAIEMENT_INVALIDE",
                    "Payez avec Wave, Orange Money, Mixx by Yas ou par carte.", "moyenPaiement");
        }
        String telephone = Telephone.normaliser(s.clientTelephone()).orElseThrow(() -> new RequeteInvalideException(
                "TELEPHONE_INVALIDE", "Indiquez un numéro sénégalais à 9 chiffres.", "clientTelephone"));

        Terrain terrain = terrains.verrouiller(s.terrainId())
                .filter(t -> t.isActif() && t.getSport().isActif())
                .orElseThrow(() -> new RequeteInvalideException("TERRAIN_INDISPONIBLE",
                        "Ce terrain n'est pas réservable.", "terrainId"));
        Creneaux.Creneau creneau = creneaux.commencantA(terrain, s.debut()).orElseThrow(() ->
                new RequeteInvalideException("CRENEAU_INVALIDE", "Ce créneau n'existe pas pour ce terrain.", "debut"));
        if (!creneau.debut().isAfter(maintenant)) {
            throw new ConflitException("CRENEAU_PASSE", "Ce créneau est déjà passé.");
        }
        LocalDate limite = LocalDate.now(horloge).plusDays(Catalogue.JOURS_RESERVABLES);
        if (creneau.debut().atZone(Horloge.DAKAR).toLocalDate().isAfter(limite)) {
            throw new RequeteInvalideException("DATE_HORS_PERIODE", "On ne réserve pas aussi loin à l'avance.", "debut");
        }
        if (blocages.entre(creneau.debut(), creneau.fin()).stream()
                .anyMatch(b -> b.chevauche(terrain.getId(), creneau.debut(), creneau.fin()))) {
            throw new ConflitException("CRENEAU_BLOQUE", "Ce créneau est fermé à la réservation.");
        }
        if (reservations.chevauchement(terrain.getId(), creneau.debut(), creneau.fin(), OCCUPANTS)) {
            throw new ConflitException("CRENEAU_PRIS", "Ce créneau vient d'être réservé. Choisissez-en un autre.");
        }

        String jeton = canal == Canal.APP ? JetonSuivi.generer() : null;
        Reservation r = reservations.save(new Reservation(nouvelleReference(), terrain, creneau.debut(), creneau.fin(),
                creneau.prix(), canal, s.moyenPaiement(), s.clientNom().trim(), telephone,
                jeton == null ? null : JetonSuivi.empreinte(jeton), JetonSuivi.codeTicket(),
                UtilisateurCourant.identifiant().orElse(null)));

        Paiement paiement = null;
        if (canal == Canal.APP) {
            IssuePaiement issue = paiements.payerEnLigne(r);
            paiement = new Paiement(issue.reference(), issue.redirectUrl(), issue.simule());
            journal.enregistrer(Domaine.RESERVATION, "CREATION", r.getReference(), "réservation " + r.getReference()
                    + " de " + terrain.getNom() + " (" + fcfa(r.getMontant()) + ") en ligne");
        } else {
            paiements.encaisserAuComptoir(r);
            journal.enregistrer(Domaine.RESERVATION, "COMPTOIR", r.getReference(), "a enregistré la réservation "
                    + r.getReference() + " de " + terrain.getNom() + " au comptoir (" + fcfa(r.getMontant()) + ", "
                    + s.moyenPaiement().code() + ")");
        }
        evenements.save(new EvenementSortant(r.getStatut() == StatutReservation.CONFIRMEE
                ? EvenementSortant.Type.RESERVATION_CONFIRMEE : EvenementSortant.Type.STATUT_CHANGE, r.getId(), maintenant));
        publication.publishEvent(new ChangementPublic(ChangementPublic.Sujet.DISPONIBILITES));

        ReservationVue vue = canal == Canal.APP ? ReservationVue.pourClient(r) : ReservationVue.pourPersonnel(r);
        String reponse = json.writeValueAsString(new ReservationCreee(vue, jeton, paiement));
        if (cleIdempotence != null) {
            idempotence.memoriser(cleIdempotence, r.getId(), reponse);
        }
        return reponse;
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
