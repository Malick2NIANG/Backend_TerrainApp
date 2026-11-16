package sn.terrainapp.backend.paiements.application;

import static sn.terrainapp.backend.commun.audit.Libelles.fcfa;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.terrainapp.backend.commun.audit.JournalActions;
import sn.terrainapp.backend.commun.audit.JournalActions.Domaine;
import sn.terrainapp.backend.commun.diffusion.ChangementPublic;
import sn.terrainapp.backend.commun.erreurs.ConflitException;
import sn.terrainapp.backend.commun.erreurs.RessourceIntrouvableException;
import sn.terrainapp.backend.paiements.application.PaiementService.IssuePaiement;
import sn.terrainapp.backend.paiements.application.PaymentProvider.NotificationPaiement;
import sn.terrainapp.backend.paiements.domaine.NotificationRecue;
import sn.terrainapp.backend.paiements.domaine.Paiement;
import sn.terrainapp.backend.paiements.domaine.StatutPaiement;
import sn.terrainapp.backend.paiements.infrastructure.NotificationRecueRepository;
import sn.terrainapp.backend.paiements.infrastructure.PaiementRepository;
import sn.terrainapp.backend.reservations.application.JetonSuivi;
import sn.terrainapp.backend.reservations.domaine.Canal;
import sn.terrainapp.backend.reservations.domaine.Reservation;
import sn.terrainapp.backend.reservations.domaine.StatutReservation;
import sn.terrainapp.backend.reservations.infrastructure.ReservationRepository;
import sn.terrainapp.backend.tempsreel.EvenementSortant;
import sn.terrainapp.backend.tempsreel.EvenementSortantRepository;

/**
 * Cycle du paiement en ligne apres la creation de la reservation :
 * relance par le joueur, notification du prestataire (traitee une seule
 * fois), et expiration : une reservation non payee dans le delai libere son
 * creneau. Un paiement arrive apres coup est signale pour remboursement.
 */
@Service
public class SuiviPaiements {

    private static final Logger LOG = LoggerFactory.getLogger(SuiviPaiements.class);

    public enum Resultat { TRAITEE, DEJA_TRAITEE, REFERENCE_INCONNUE, MONTANT_INCOHERENT }

    private final PaymentProvider prestataire;
    private final PaiementService paiementService;
    private final PaiementRepository paiements;
    private final NotificationRecueRepository notifications;
    private final ReservationRepository reservations;
    private final EvenementSortantRepository evenements;
    private final ApplicationEventPublisher publication;
    private final JournalActions journal;
    private final Clock horloge;
    private final Duration delaiExpiration;

    public SuiviPaiements(PaymentProvider prestataire, PaiementService paiementService, PaiementRepository paiements,
                          NotificationRecueRepository notifications, ReservationRepository reservations,
                          EvenementSortantRepository evenements, ApplicationEventPublisher publication,
                          JournalActions journal, Clock horloge,
                          @Value("${app.paiement.delai-expiration-minutes:15}") long delaiExpirationMinutes) {
        this.prestataire = prestataire;
        this.paiementService = paiementService;
        this.paiements = paiements;
        this.notifications = notifications;
        this.reservations = reservations;
        this.evenements = evenements;
        this.publication = publication;
        this.journal = journal;
        this.horloge = horloge;
        this.delaiExpiration = Duration.ofMinutes(delaiExpirationMinutes);
    }

    public record PaiementInitVue(String paymentReference, String redirectUrl, StatutReservation statut, boolean simule) {
    }

    /** Relance du paiement d'une reservation encore en attente (detenteur du jeton seulement, sinon 404). */
    @Transactional
    public PaiementInitVue relancer(long reservationId, String jeton) {
        Reservation r = reservations.verrouiller(reservationId)
                .filter(x -> x.getCanal() == Canal.APP)
                .filter(x -> JetonSuivi.correspond(jeton, x.getJetonSuiviEmpreinte()))
                .orElseThrow(() -> new RessourceIntrouvableException("Réservation introuvable."));
        if (r.getStatut() != StatutReservation.EN_ATTENTE_PAIEMENT) {
            throw new ConflitException("PAIEMENT_IMPOSSIBLE", r.getStatut() == StatutReservation.CONFIRMEE
                    ? "Cette réservation est déjà payée." : "Cette réservation n'est plus valable : réservez à nouveau.");
        }
        IssuePaiement issue = paiementService.payerEnLigne(r);
        apresChangement(r, Instant.now(horloge));
        journal.enregistrer(Domaine.PAIEMENT, "RELANCE", r.getReference(), "paiement relancé pour la réservation " + r.getReference());
        return new PaiementInitVue(issue.reference(), issue.redirectUrl(), r.getStatut(), issue.simule());
    }

    /** Notification du prestataire (signature verifiee par le prestataire, 401 sinon). */
    @Transactional
    public Resultat traiterNotification(byte[] corps, Map<String, String> entetes) {
        NotificationPaiement n = prestataire.lireNotification(corps, entetes);
        Instant maintenant = Instant.now(horloge);
        if (notifications.existsByPrestataireAndEvenementId(prestataire.code(), n.evenementId())) {
            return Resultat.DEJA_TRAITEE;
        }
        notifications.saveAndFlush(new NotificationRecue(prestataire.code(), n.evenementId(), n.reference(), n.statut(),
                n.montant(), maintenant));
        Paiement p = paiements.findByPrestataireAndReference(prestataire.code(), n.reference()).orElse(null);
        if (p == null) {
            LOG.warn("Notification de paiement pour une reference inconnue (evenement {})", n.evenementId());
            return Resultat.REFERENCE_INCONNUE;
        }
        Reservation r = reservations.verrouiller(p.getReservationId()).orElseThrow();
        if (n.montant() != r.getMontant()) {
            journal.enregistrerEnTantQue("prestataire", Domaine.PAIEMENT, "ANOMALIE", r.getReference(), "paiement de "
                    + fcfa(n.montant()) + " notifié pour la réservation " + r.getReference() + " (attendu : "
                    + fcfa(r.getMontant()) + ") : ignoré");
            return Resultat.MONTANT_INCOHERENT;
        }
        p.definirStatut(n.statut());
        if (n.statut() == StatutPaiement.PAYE) {
            if (r.getStatut() == StatutReservation.EN_ATTENTE_PAIEMENT) {
                r.confirmerPaiement();
                apresChangement(r, maintenant);
                journal.enregistrerEnTantQue("prestataire", Domaine.PAIEMENT, "CONFIRME", r.getReference(),
                        "paiement confirmé pour la réservation " + r.getReference() + " (" + fcfa(r.getMontant()) + ")");
            } else if (r.paiementTardif()) {
                journal.enregistrerEnTantQue("prestataire", Domaine.PAIEMENT, "TARDIF", r.getReference(),
                        "paiement reçu après l'expiration de la réservation " + r.getReference()
                                + " — remboursement de " + fcfa(r.getMontant()) + " à faire");
            }
        } else if (n.statut() == StatutPaiement.ECHOUE && r.getStatut() == StatutReservation.EN_ATTENTE_PAIEMENT) {
            r.expirer();
            apresChangement(r, maintenant);
            journal.enregistrerEnTantQue("prestataire", Domaine.PAIEMENT, "ECHOUE", r.getReference(),
                    "paiement refusé : réservation " + r.getReference() + " libérée");
        }
        return Resultat.TRAITEE;
    }

    /** Toutes les minutes : les reservations non payees dans le delai liberent leur creneau. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public int expirer() {
        Instant maintenant = Instant.now(horloge);
        List<Long> ids = reservations.enAttenteDePaiementAvant(maintenant.minus(delaiExpiration), PageRequest.of(0, 200));
        int expirees = 0;
        for (Long id : ids) {
            Reservation r = reservations.verrouiller(id).orElse(null);
            if (r != null && r.getStatut() == StatutReservation.EN_ATTENTE_PAIEMENT) {
                r.expirer();
                apresChangement(r, maintenant);
                journal.enregistrerEnTantQue("systeme", Domaine.PAIEMENT, "EXPIRE", r.getReference(), "réservation "
                        + r.getReference() + " libérée : paiement non reçu sous " + delaiExpiration.toMinutes() + " min");
                expirees++;
            }
        }
        return expirees;
    }

    private void apresChangement(Reservation r, Instant quand) {
        evenements.save(new EvenementSortant(r.getStatut() == StatutReservation.CONFIRMEE
                ? EvenementSortant.Type.RESERVATION_CONFIRMEE : EvenementSortant.Type.STATUT_CHANGE, r.getId(), quand));
        publication.publishEvent(new ChangementPublic(ChangementPublic.Sujet.DISPONIBILITES));
    }
}
