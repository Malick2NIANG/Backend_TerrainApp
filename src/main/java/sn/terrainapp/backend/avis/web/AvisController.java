package sn.terrainapp.backend.avis.web;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import sn.terrainapp.backend.avis.application.AvisService;
import sn.terrainapp.backend.avis.application.AvisService.AvisVue;
import sn.terrainapp.backend.avis.application.AvisService.SaisieAvis;
import sn.terrainapp.backend.commun.web.CacheHttp;

@RestController
@Tag(name = "Avis")
public class AvisController {

    public record Visibilite(boolean visible) {
    }

    private final AvisService service;

    public AvisController(AvisService service) {
        this.service = service;
    }

    @PostMapping("/reservations/{id}/avis")
    @Operation(summary = "Noter le terrain apres le match (en-tete X-Jeton-Suivi ; un avis par reservation)")
    public ResponseEntity<AvisVue> deposer(@PathVariable long id, @RequestHeader(name = "X-Jeton-Suivi", required = false) String jeton,
                                           @Valid @RequestBody SaisieAvis saisie) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.deposer(id, jeton, saisie));
    }

    @GetMapping("/terrains/{id}/avis")
    @Operation(summary = "Avis visibles d'un terrain (30 plus recents)")
    public ResponseEntity<List<AvisVue>> duTerrain(@PathVariable long id) {
        return CacheHttp.lecture(service.duTerrain(id));
    }

    @GetMapping("/avis")
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Tous les avis (gerant), visibles ou masques")
    public List<AvisVue> tous() {
        return service.tous();
    }

    @PatchMapping("/avis/{id}")
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Masquer ou reafficher un avis")
    public AvisVue visibilite(@PathVariable long id, @RequestBody Visibilite v) {
        return service.rendreVisible(id, v.visible());
    }
}
