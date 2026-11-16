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

import sn.terrainapp.backend.commun.audit.JournalActions;
import sn.terrainapp.backend.commun.audit.JournalActions.Domaine;
import sn.terrainapp.backend.commun.diffusion.ChangementPublic;
import sn.terrainapp.backend.commun.erreurs.RequeteInvalideException;
import sn.terrainapp.backend.commun.erreurs.RessourceIntrouvableException;
import sn.terrainapp.backend.commun.securite.UtilisateurCourant;
import sn.terrainapp.backend.commun.telephone.Telephone;
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
    private final TentativesPin tentatives;

    /** Reservation retrouvee par code PIN, avec son jeton d'acces (90 jours). */
    public record ReservationRetrouvee(ReservationVue reservation, String jetonAcces) {
    }

    public SuiviReservations(ReservationRepository reservations, EvenementSortantRepository evenements,
                             ApplicationEventPublisher publication, JournalActions journal, Clock horloge,
                             JetonsAcces jetonsAcces, TentativesPin tentatives) {
        this.jetonsAcces = jetonsAcces;
        this.tentatives = tentatives;
        this.reservations = reservations;
        this.evenements = evenements;
        this.publication = publication;
        this.journal = journal;
        this.horloge = horloge;
    }

    /**
     * Joueur : uniquement avec le jeton de suivi, ou le jeton d'acces obtenu
     * par code PIN (sinon 404, on ne confirme meme pas l'existence).
     */
    @Transactional(readOnly = true)
    public ReservationVue pourClient(long id, String jeton) {
        return reservations.findById(id).filter(r -> jetonsAcces.autorise(id, jeton, r.getJetonSuiviEmpreinte()))
                .map(ReservationVue::pourClient)
                .orElseThrow(() -> new RessourceIntrouvableException("Réservation introuvable."));
    }

    /**
     * Joueur sur un autre telephone : numero + code PIN choisi a la
     * reservation. Seules les reservations protegees par CE code sont
     * rendues (rien sans le bon code : le numero seul n'expose plus rien).
     * 5 echecs en 15 min bloquent le numero (TentativesPin).
     */
    @Transactional(readOnly = true)
    public List<ReservationRetrouvee> retrouver(String saisieTelephone, String pin) {
        String telephone = Telephone.normaliser(saisieTelephone).orElseThrow(() -> new RequeteInvalideException(
                "TELEPHONE_REQUIS", "Indiquez un numéro sénégalais à 9 chiffres.", "telephone"));
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new RequeteInvalideException("PIN_INVALIDE", "Le code PIN fait 4 chiffres.", "pin");
        }
        tentatives.verifierAutorise(telephone);
        List<ReservationRetrouvee> trouvees = reservations
                .findTop60ByClientTelephoneAndPinEmpreinteIsNotNullOrderByDebutDesc(telephone).stream()
                .filter(r -> CodesPin.correspond(pin, r.getPinEmpreinte()))
                .map(r -> new ReservationRetrouvee(ReservationVue.pourClient(r), jetonsAcces.emettre(r.getId())))
                .toList();
        if (trouvees.isEmpty()) {
            tentatives.echec(telephone);
        } else {
            tentatives.reussite(telephone);
        }
        return trouvees;
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
