package sn.terrainapp.backend.annonces.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import sn.terrainapp.backend.annonces.application.AnnonceSaisie;
import sn.terrainapp.backend.annonces.application.AnnonceService;
import sn.terrainapp.backend.annonces.application.AnnonceVue;
import sn.terrainapp.backend.annonces.domaine.StatutAnnonce;

@RestController
@RequestMapping("/annonces")
@Tag(name = "Annonces")
public class AnnonceController {

    public record ChangementStatut(@NotNull StatutAnnonce statut, Long version) {
    }

    private final AnnonceService service;

    public AnnonceController(AnnonceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Annonces publiees (toutes pour le gerant)")
    public List<AnnonceVue> lister(@RequestParam(required = false) StatutAnnonce statut) {
        return service.lister(statut);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GERANT')")
    public AnnonceVue creer(@Valid @RequestBody AnnonceSaisie saisie) {
        return service.creer(saisie);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GERANT')")
    public AnnonceVue modifier(@PathVariable long id, @Valid @RequestBody AnnonceSaisie saisie) {
        return service.modifier(id, saisie);
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Publier (la popup reapparait), retirer, archiver")
    public AnnonceVue statut(@PathVariable long id, @Valid @RequestBody ChangementStatut saisie) {
        return service.changerStatut(id, saisie.statut(), saisie.version());
    }
}
