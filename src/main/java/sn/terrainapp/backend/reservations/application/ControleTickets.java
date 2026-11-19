package sn.terrainapp.backend.reservations.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.terrainapp.backend.commun.audit.JournalActions;
import sn.terrainapp.backend.commun.audit.JournalActions.Domaine;
import sn.terrainapp.backend.commun.securite.UtilisateurCourant;
import sn.terrainapp.backend.reservations.domaine.Reservation;
import sn.terrainapp.backend.reservations.infrastructure.ReservationRepository;
import sn.terrainapp.backend.tempsreel.EvenementSortant;
import sn.terrainapp.backend.tempsreel.EvenementSortantRepository;

/**
 * Scan du QR a l'entree. Valide d'une marge avant le debut jusqu'a la fin du
 * creneau, une seule fois. La reponse dit toujours pourquoi un ticket est
 * refuse : le controleur l'explique au joueur. Saisie de la reference
 * (ex. R7K3P9Q) acceptee en secours si le QR est illisible.
 */
@Service
public class ControleTickets {

    private static final java.util.regex.Pattern REFERENCE = java.util.regex.Pattern.compile("^R[A-Z2-9]{6}$");

    public enum Verdict { VALIDE, DEJA_UTILISE, TROP_TOT, EXPIRE, NON_PAYE, ANNULE, INCONNU }

    public record ResultatScan(Verdict verdict, String message, ReservationVue reservation) {
    }

    private final ReservationRepository reservations;
    private final EvenementSortantRepository evenements;
    private final JournalActions journal;
    private final Clock horloge;
    private final Duration margeAvant;

    public ControleTickets(ReservationRepository reservations, EvenementSortantRepository evenements,
                           JournalActions journal, Clock horloge,
                           sn.terrainapp.backend.complexe.IdentiteComplexe identite) {
        this.reservations = reservations;
        this.evenements = evenements;
        this.journal = journal;
        this.horloge = horloge;
        this.margeAvant = Duration.ofMinutes(identite.margeEntreeMinutes());
    }

    @Transactional
    public ResultatScan scanner(String code) {
        if (code == null || code.isBlank()) {
            return new ResultatScan(Verdict.INCONNU, "QR code illisible.", null);
        }
        // QR du ticket (code aleatoire), ou reference saisie a la main en secours.
        String saisi = code.trim();
        Reservation r = REFERENCE.matcher(saisi.toUpperCase()).matches()
                ? reservations.verrouillerParReference(saisi.toUpperCase()).orElse(null)
                : reservations.verrouillerParTicket(saisi).orElse(null);
        if (r == null) {
            return new ResultatScan(Verdict.INCONNU, "Ticket inconnu.", null);
        }
        ReservationVue vue = ReservationVue.pourPersonnel(r);
        Instant maintenant = Instant.now(horloge);
        return switch (r.getStatut()) {
            case HONOREE -> new ResultatScan(Verdict.DEJA_UTILISE, "Ticket déjà utilisé.", vue);
            case EN_ATTENTE_PAIEMENT, EXPIREE -> new ResultatScan(Verdict.NON_PAYE, "Réservation non payée.", vue);
            case ANNULEE -> new ResultatScan(Verdict.ANNULE, "Réservation annulée.", vue);
            case CONFIRMEE -> {
                if (maintenant.isBefore(r.getDebut().minus(margeAvant))) {
                    yield new ResultatScan(Verdict.TROP_TOT, "Trop tôt : entrée " + margeAvant.toMinutes()
                            + " min avant le début.", vue);
                }
                if (!maintenant.isBefore(r.getFin())) {
                    yield new ResultatScan(Verdict.EXPIRE, "Créneau terminé.", vue);
                }
                r.honorer(UtilisateurCourant.identifiant().orElse("systeme"), maintenant);
                evenements.save(new EvenementSortant(EvenementSortant.Type.STATUT_CHANGE, r.getId(), maintenant));
                journal.enregistrer(Domaine.RESERVATION, "ENTREE", r.getReference(), "a validé l'entrée de la réservation "
                        + r.getReference());
                yield new ResultatScan(Verdict.VALIDE, "Entrée autorisée.", ReservationVue.pourPersonnel(r));
            }
        };
    }
}
