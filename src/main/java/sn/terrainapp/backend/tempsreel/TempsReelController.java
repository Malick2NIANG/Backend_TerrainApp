package sn.terrainapp.backend.tempsreel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import sn.terrainapp.backend.reservations.infrastructure.ReservationRepository;

/**
 * Flux temps reel du planning (Server-Sent Events), reserve au staff
 * authentifie. Le Manager s'y connecte avec fetch() en streaming pour
 * envoyer son en-tete Authorization (un EventSource de navigateur ne le
 * peut pas, et un jeton dans l'URL finirait dans les journaux).
 *
 * Reconnexion : le navigateur renvoie Last-Event-ID ; les evenements manques
 * (24 dernieres heures) sont renvoyes avant le direct.
 *
 * Derriere nginx : bloc dedie (proxy_buffering off, delai long), voir le
 * document "Reste a faire".
 */
@RestController
@Tag(name = "Temps reel")
public class TempsReelController {

    static final Duration DUREE_CONNEXION = Duration.ofMinutes(30);
    static final Duration RATTRAPAGE = Duration.ofHours(24);

    private final FluxPlanning flux;
    private final FluxPublic fluxPublic;
    private final EvenementSortantRepository evenements;
    private final ReservationRepository reservations;
    private final Clock horloge;

    public TempsReelController(FluxPlanning flux, EvenementSortantRepository evenements,
                               ReservationRepository reservations, Clock horloge, FluxPublic fluxPublic) {
        this.fluxPublic = fluxPublic;
        this.flux = flux;
        this.evenements = evenements;
        this.reservations = reservations;
        this.horloge = horloge;
    }

    @GetMapping(path = "/temps-reel/planning", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('GERANT', 'ACCUEIL', 'CONTROLEUR', 'ADMIN')")
    @Operation(summary = "Flux SSE du planning (evenement 'reservation')")
    @Transactional(readOnly = true)
    public SseEmitter abonner(@RequestHeader(name = "Last-Event-ID", required = false) String dernierEvenement) {
        SseEmitter emetteur = new SseEmitter(DUREE_CONNEXION.toMillis());
        try {
            // Premier message immediat : les en-tetes partent tout de suite (le poste
            // sait qu'il est connecte) et le navigateur apprend le delai de
            // reconnexion a utiliser en cas de coupure.
            emetteur.send(SseEmitter.event().comment("connecte").reconnectTime(5_000));
        } catch (java.io.IOException deconnecte) {
            emetteur.completeWithError(deconnecte);
            return emetteur;
        }
        if (dernierEvenement != null && dernierEvenement.matches("\\d{1,18}")) {
            Instant depuis = Instant.now(horloge).minus(RATTRAPAGE);
            evenements.publiesApres(Long.parseLong(dernierEvenement), depuis).stream().limit(200)
                    .forEach(e -> reservations.findById(e.getReservationId())
                            .ifPresent(r -> flux.envoyer(emetteur, e.getId(), e.getType(), r)));
        }
        flux.abonner(emetteur);
        return emetteur;
    }

    /**
     * Flux PUBLIC de l'App Client (sans compte, EventSource) : evenement
     * 'changement' {sujet} quand les terrains ou les disponibilites changent. 503 si le
     * serveur a atteint sa limite de connexions (le client recharge alors
     * au retour sur l'app).
     */
    @GetMapping(path = "/temps-reel/public", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Flux SSE public : sujets publics modifies (evenement 'changement')")
    public org.springframework.http.ResponseEntity<SseEmitter> publicClient() {
        SseEmitter e = fluxPublic.abonner();
        return e == null ? org.springframework.http.ResponseEntity.status(503).build()
                : org.springframework.http.ResponseEntity.ok(e);
    }
}
