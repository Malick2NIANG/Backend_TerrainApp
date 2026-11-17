package sn.terrainapp.backend.reservations.application;

import static sn.terrainapp.backend.commun.audit.Libelles.fcfa;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.terrainapp.backend.avis.infrastructure.AvisRepository;
import sn.terrainapp.backend.commun.audit.JournalActions;
import sn.terrainapp.backend.commun.audit.JournalActions.Domaine;
import sn.terrainapp.backend.commun.diffusion.ChangementPublic;
import sn.terrainapp.backend.commun.erreurs.ConflitException;
import sn.terrainapp.backend.commun.erreurs.RequeteInvalideException;
import sn.terrainapp.backend.commun.erreurs.RessourceIntrouvableException;
import sn.terrainapp.backend.commun.securite.UtilisateurCourant;
import sn.terrainapp.backend.commun.temps.Horloge;
import sn.terrainapp.backend.marque.Marque;
import sn.terrainapp.backend.reservations.domaine.Canal;
import sn.terrainapp.backend.reservations.domaine.Reservation;
import sn.terrainapp.backend.security.Role;
import sn.terrainapp.backend.sms.EnvoiSms;
import sn.terrainapp.backend.reservations.domaine.StatutReservation;
import sn.terrainapp.backend.reservations.infrastructure.ReservationRepository;
import sn.terrainapp.backend.tempsreel.EvenementSortant;
import sn.terrainapp.backend.tempsreel.EvenementSortantRepository;

/** Lectures des reservations (joueur, personnel) et annulation par le personnel. */
@Service
public class SuiviReservations {

    private final ReservationRepository reservations;
    private final EvenementSortantRepository evenements;
    private final ApplicationEventPublisher publication;
    private final JournalActions journal;
    private final Clock horloge;
    private final JetonsAcces jetonsAcces;
    private final AvisRepository avis;
    private final EnvoiSms sms;
    private final Marque marque;
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("d/MM 'à' HH:mm");

    /** Reservation retrouvee par code SMS, avec son jeton d'acces (90 jours). */
    public record ReservationRetrouvee(ReservationVue reservation, String jetonAcces) {
    }

    public SuiviReservations(ReservationRepository reservations, EvenementSortantRepository evenements,
                             ApplicationEventPublisher publication, JournalActions journal, Clock horloge,
                             JetonsAcces jetonsAcces, AvisRepository avis, EnvoiSms sms, Marque marque) {
        this.avis = avis;
        this.sms = sms;
        this.marque = marque;
        this.jetonsAcces = jetonsAcces;
        this.reservations = reservations;
        this.evenements = evenements;
        this.publication = publication;
        this.journal = journal;
        this.horloge = horloge;
    }

    /**
     * Joueur : uniquement avec le jeton de suivi, ou le jeton d'acces obtenu
     * par code SMS (sinon 404, on ne confirme meme pas l'existence).
     */
    @Transactional(readOnly = true)
    public ReservationVue pourClient(long id, String jeton) {
        return reservations.findById(id).filter(r -> jetonsAcces.autorise(id, jeton, r.getJetonSuiviEmpreinte()))
                .map(r -> ReservationVue.pourClient(r).avecAvis(avis.findByReservationId(id).map(a -> a.getNote()).orElse(null)))
                .orElseThrow(() -> new RessourceIntrouvableException("Réservation introuvable."));
    }

    /**
     * Reservations d'un numero (60 dernieres), chacune avec un jeton d'acces.
     * Appele uniquement apres verification du code SMS (RetrouverParSms).
     */
    @Transactional(readOnly = true)
    public List<ReservationRetrouvee> duNumero(String telephone) {
        return reservations.findTop60ByClientTelephoneOrderByDebutDesc(telephone).stream()
                .map(r -> new ReservationRetrouvee(ReservationVue.pourClient(r)
                        .avecAvis(avis.findByReservationId(r.getId()).map(a -> a.getNote()).orElse(null)),
                        jetonsAcces.emettre(r.getId())))
                .toList();
    }

    /** Personnel : reservations d'un jour (de 00:00 au lendemain 06:00, pour la nuit). */
    @Transactional(readOnly = true)
    public List<ReservationVue> planning(LocalDate jour) {
        Instant debut = LocalDateTime.of(jour, LocalTime.MIN).atZone(Horloge.DAKAR).toInstant();
        Instant fin = LocalDateTime.of(jour.plusDays(1), LocalTime.of(6, 0)).atZone(Horloge.DAKAR).toInstant();
        return reservations.entre(debut, fin, EnumSet.allOf(StatutReservation.class)).stream()
                .map(ReservationVue::pourPersonnel).toList();
    }

    /**
     * Reimpression du recu au comptoir (client qui a perdu son ticket) : le
     * code du ticket est rendu pour le QR, et la reimpression est journalisee.
     */
    @Transactional
    public ReservationVue pourRecu(long id) {
        Reservation r = reservations.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Réservation introuvable."));
        if (r.getPayeeLe() == null || (r.getStatut() != StatutReservation.CONFIRMEE && r.getStatut() != StatutReservation.HONOREE)) {
            throw new ConflitException("RECU_INDISPONIBLE", "Pas de reçu : la réservation n'est pas payée.");
        }
        journal.enregistrer(Domaine.RESERVATION, "RECU", r.getReference(), "a imprimé le reçu de la réservation " + r.getReference());
        return ReservationVue.pourClient(r);
    }

    @Transactional(readOnly = true)
    public ReservationVue pourPersonnel(long id) {
        return reservations.findById(id).map(ReservationVue::pourPersonnel)
                .orElseThrow(() -> new RessourceIntrouvableException("Réservation introuvable."));
    }

    /**
     * Annulation par l'accueil ou le gerant, avec un motif (le client est
     * prevenu par SMS). Impossible une fois le creneau commence. Une
     * reservation payee en ligne (a rembourser) : le gerant seulement.
     */
    @Transactional
    public ReservationVue annulerParPersonnel(long id, String motifSaisi) {
        String motif = motifSaisi == null ? "" : motifSaisi.trim();
        if (motif.length() < 3 || motif.length() > 200) {
            throw new RequeteInvalideException("MOTIF_REQUIS", "Indiquez le motif de l'annulation.", "motif");
        }
        Reservation r = reservations.verrouiller(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Réservation introuvable."));
        Instant maintenant = Instant.now(horloge);
        if (!r.getDebut().isAfter(maintenant)) {
            throw new ConflitException("CRENEAU_COMMENCE", "Le créneau a commencé : la réservation ne peut plus être annulée.");
        }
        boolean aRembourser = r.getCanal() == Canal.APP && r.getPayeeLe() != null;
        if (aRembourser && UtilisateurCourant.role().filter(x -> x == Role.GERANT).isEmpty()) {
            throw new AccessDeniedException("Réservation payée en ligne : seul le gérant peut l'annuler (remboursement à faire).");
        }
        r.annuler(UtilisateurCourant.identifiant().orElse("systeme"), maintenant, motif);
        evenements.save(new EvenementSortant(EvenementSortant.Type.STATUT_CHANGE, r.getId(), maintenant));
        publication.publishEvent(new ChangementPublic(ChangementPublic.Sujet.DISPONIBILITES));
        journal.enregistrer(Domaine.RESERVATION, "ANNULATION", r.getReference(), "a annulé la réservation " + r.getReference()
                + " (" + motif + ")" + (aRembourser ? " : " + fcfa(r.getMontant()) + " à rembourser" : ""));
        sms.envoyer(r.getClientTelephone(), marque.nom() + " : votre réservation " + r.getReference() + " du "
                + JOUR.format(r.getDebut().atZone(Horloge.DAKAR)) + " est annulée (" + motif + ")."
                + (aRembourser ? " Vous serez remboursé de " + fcfa(r.getMontant()) + "." : ""));
        return ReservationVue.pourPersonnel(r);
    }
}
