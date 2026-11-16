package sn.terrainapp.backend.avis.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import sn.terrainapp.backend.avis.domaine.Avis;
import sn.terrainapp.backend.avis.infrastructure.AvisRepository;
import sn.terrainapp.backend.commun.audit.JournalActions;
import sn.terrainapp.backend.commun.audit.JournalActions.Domaine;
import sn.terrainapp.backend.commun.diffusion.ChangementPublic;
import sn.terrainapp.backend.commun.erreurs.ConflitException;
import sn.terrainapp.backend.commun.erreurs.RessourceIntrouvableException;
import sn.terrainapp.backend.reservations.application.JetonsAcces;
import sn.terrainapp.backend.reservations.domaine.Reservation;
import sn.terrainapp.backend.reservations.domaine.StatutReservation;
import sn.terrainapp.backend.reservations.infrastructure.ReservationRepository;

/**
 * Avis des joueurs : un par reservation, une fois le creneau joue (ticket
 * scanne, ou creneau termine), par le detenteur du jeton de la reservation.
 */
@Service
@Transactional
public class AvisService {

    public record SaisieAvis(@Min(1) @Max(5) int note, @Size(max = 500) String commentaire) {
    }

    public record AvisVue(long id, long terrainId, int note, String commentaire, String auteur, boolean visible,
                          Instant creeLe) {
        public static AvisVue de(Avis a) {
            return new AvisVue(a.getId(), a.getTerrainId(), a.getNote(), a.getCommentaire(), a.getAuteur(), a.isVisible(),
                    a.getCreeLe());
        }
    }

    /** Note moyenne (arrondie a 0,1) et nombre d'avis visibles d'un terrain. */
    public record Stats(double moyenne, long nombre) {
    }

    private final AvisRepository avis;
    private final ReservationRepository reservations;
    private final JetonsAcces jetonsAcces;
    private final JournalActions journal;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    public AvisService(AvisRepository avis, ReservationRepository reservations, JetonsAcces jetonsAcces,
                       JournalActions journal, ApplicationEventPublisher evenements, Clock horloge) {
        this.avis = avis;
        this.reservations = reservations;
        this.jetonsAcces = jetonsAcces;
        this.journal = journal;
        this.evenements = evenements;
        this.horloge = horloge;
    }

    public AvisVue deposer(long reservationId, String jeton, SaisieAvis s) {
        // En ligne (jeton de suivi) ou au comptoir avec un code PIN (jeton d'acces).
        Reservation r = reservations.findById(reservationId)
                .filter(x -> jetonsAcces.autorise(reservationId, jeton, x.getJetonSuiviEmpreinte()))
                .orElseThrow(() -> new RessourceIntrouvableException("Réservation introuvable."));
        if (!jouee(r, Instant.now(horloge))) {
            throw new ConflitException("AVIS_TROP_TOT", "Vous pourrez noter le terrain après votre match.");
        }
        if (avis.findByReservationId(reservationId).isPresent()) {
            throw new ConflitException("AVIS_DEJA_DONNE", "Vous avez déjà noté ce match. Merci !");
        }
        Avis a = avis.save(new Avis(reservationId, r.getTerrain().getId(), s.note(), s.commentaire(),
                Avis.auteurDe(r.getClientNom())));
        evenements.publishEvent(new ChangementPublic(ChangementPublic.Sujet.TERRAINS));
        return AvisVue.de(a);
    }

    /** Match joue : ticket scanne a l'entree, ou creneau paye et termine. */
    public static boolean jouee(Reservation r, Instant maintenant) {
        return r.getStatut() == StatutReservation.HONOREE
                || (r.getStatut() == StatutReservation.CONFIRMEE && r.getFin().isBefore(maintenant));
    }

    @Transactional(readOnly = true)
    public List<AvisVue> duTerrain(long terrainId) {
        return avis.findByTerrainIdAndVisibleTrueOrderByCreeLeDesc(terrainId, PageRequest.of(0, 30)).stream()
                .map(AvisVue::de).toList();
    }

    @Transactional(readOnly = true)
    public List<AvisVue> tous() {
        return avis.findAllByOrderByCreeLeDesc(PageRequest.of(0, 300)).stream().map(AvisVue::de).toList();
    }

    public AvisVue rendreVisible(long id, boolean visible) {
        Avis a = avis.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Avis introuvable."));
        a.rendreVisible(visible);
        journal.enregistrer(Domaine.TERRAIN, visible ? "AVIS_AFFICHE" : "AVIS_MASQUE", String.valueOf(id),
                (visible ? "a réaffiché" : "a masqué") + " un avis de " + a.getAuteur());
        evenements.publishEvent(new ChangementPublic(ChangementPublic.Sujet.TERRAINS));
        return AvisVue.de(a);
    }

    @Transactional(readOnly = true)
    public Map<Long, Stats> stats() {
        Map<Long, Stats> m = new HashMap<>();
        for (Object[] l : avis.statsParTerrain()) {
            m.put((Long) l[0], new Stats(Math.round(((Number) l[1]).doubleValue() * 10) / 10.0, (Long) l[2]));
        }
        return m;
    }

    /** Note donnee pour ces reservations (affichage "votre note"). */
    @Transactional(readOnly = true)
    public Map<Long, Integer> notesDe(List<Long> reservationIds) {
        Map<Long, Integer> m = new HashMap<>();
        if (!reservationIds.isEmpty()) {
            avis.findByReservationIdIn(reservationIds).forEach(a -> m.put(a.getReservationId(), a.getNote()));
        }
        return m;
    }
}
