package sn.terrainapp.backend.paiements.web;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import sn.terrainapp.backend.paiements.application.SuiviPaiements;
import sn.terrainapp.backend.paiements.application.SuiviPaiements.PaiementInitVue;
import sn.terrainapp.backend.reservations.web.ReservationController;

@RestController
@RequestMapping("/paiements")
@Tag(name = "Paiements")
public class PaiementController {

    private final SuiviPaiements suivi;

    public PaiementController(SuiviPaiements suivi) {
        this.suivi = suivi;
    }

    public record DemandeInit(@NotNull Long reservationId) {
    }

    @PostMapping("/init")
    @Operation(summary = "Relancer le paiement d'une reservation en attente (en-tete X-Jeton-Suivi obligatoire)")
    public ResponseEntity<PaiementInitVue> initier(@Valid @RequestBody DemandeInit demande,
                                                   @RequestHeader(name = ReservationController.EN_TETE_JETON, required = false) String jeton) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(suivi.relancer(demande.reservationId(), jeton));
    }

    /** Notification signee du prestataire : 200 des qu'elle est authentifiee, 401 sinon. */
    @PostMapping("/webhook")
    @Operation(summary = "Notification signee du prestataire de paiement (traitee une seule fois)")
    public Map<String, String> webhook(@RequestBody byte[] corps, @RequestHeader Map<String, String> entetes) {
        return Map.of("resultat", suivi.traiterNotification(corps, entetes).name());
    }
}
