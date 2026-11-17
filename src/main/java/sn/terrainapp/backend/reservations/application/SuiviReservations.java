package sn.terrainapp.backend.reservations.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.terrainapp.backend.avis.infrastructure.AvisRepository;
import sn.terrainapp.backend.commun.audit.JournalActions;
import sn.terrainapp.backend.commun.audit.JournalActions.Domaine;
import sn.terrainapp.backend.commun.diffusion.ChangementPublic;
import sn.terrainapp.backend.commun.erreurs.RessourceIntrouvableException;
import sn.terrainapp.backend.commun.securite.UtilisateurCourant;
import sn.terrainapp.backend.commun.temps.Horloge;
import sn.terrainapp.backend.reservations.domaine.Reservation;
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

    /** Reservation retrouvee par code SMS, avec son jeton d'acces (90 jours). */
    public record ReservationRetrouvee(ReservationVue reservation, String jetonAcces) {
    }

    public SuiviReservations(ReservationRepository reservations, EvenementSortantRepository evenements,
                             ApplicationEventPublisher publication, JournalActions journal, Clock horloge,
                             JetonsAcces jetonsAcces, AvisRepository avis) {
        this.avis = avis;
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

    @Transactional(readOnly = true)
    public ReservationVue pourPersonnel(long id) {
        return reservations.findById(id).map(ReservationVue::pourPersonnel)
                .orElseThrow(() -> new RessourceIntrouvableException("Réservation introuvable."));
    }

    /** Annulation par l'accueil ou le gerant : le creneau est libere aussitot. */
    @Transactional
    public ReservationVue annulerParPersonnel(long id) {
        Reservation r = reservations.verrouiller(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Réservation introuvable."));
        Instant maintenant = Instant.now(horloge);
        r.annuler(UtilisateurCourant.identifiant().orElse("systeme"), maintenant);
        evenements.save(new EvenementSortant(EvenementSortant.Type.STATUT_CHANGE, r.getId(), maintenant));
        publication.publishEvent(new ChangementPublic(ChangementPublic.Sujet.DISPONIBILITES));
        journal.enregistrer(Domaine.RESERVATION, "ANNULATION", r.getReference(), "a annulé la réservation " + r.getReference());
        return ReservationVue.pourPersonnel(r);
    }
}
