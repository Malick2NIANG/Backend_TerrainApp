package sn.terrainapp.backend.terrains.web;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import sn.terrainapp.backend.terrains.application.GestionTerrains;
import sn.terrainapp.backend.terrains.application.GestionTerrains.SaisieBlocage;
import sn.terrainapp.backend.terrains.application.GestionTerrains.SaisieHoraire;
import sn.terrainapp.backend.terrains.application.GestionTerrains.SaisieSport;
import sn.terrainapp.backend.terrains.application.GestionTerrains.SaisieTerrain;
import sn.terrainapp.backend.terrains.application.Vues.BlocageVue;
import sn.terrainapp.backend.terrains.application.Vues.HoraireVue;
import sn.terrainapp.backend.terrains.application.Vues.SportVue;
import sn.terrainapp.backend.terrains.application.Vues.TerrainVue;

/** Configuration du complexe par le gerant. Blocages : aussi l'accueil. */
@RestController
@Tag(name = "Gestion des terrains")
public class GestionTerrainsController {

    private final GestionTerrains gestion;

    public GestionTerrainsController(GestionTerrains gestion) {
        this.gestion = gestion;
    }

    @PostMapping("/sports")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Creer un sport")
    public SportVue creerSport(@Valid @RequestBody SaisieSport s) {
        return gestion.creerSport(s);
    }

    @PutMapping("/sports/{id}")
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Modifier un sport")
    public SportVue modifierSport(@PathVariable long id, @Valid @RequestBody SaisieSport s) {
        return gestion.modifierSport(id, s);
    }

    @DeleteMapping("/sports/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Supprimer un sport sans terrain")
    public void supprimerSport(@PathVariable long id) {
        gestion.supprimerSport(id);
    }

    @PostMapping("/terrains")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Creer un terrain (tarifs et plages tarifaires compris)")
    public TerrainVue creerTerrain(@Valid @RequestBody SaisieTerrain s) {
        return gestion.creerTerrain(s);
    }

    public record DemandeStatut(sn.terrainapp.backend.terrains.domaine.StatutTerrain statut) {
    }

    @org.springframework.web.bind.annotation.PatchMapping("/terrains/{id}/statut")
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Publier, depublier (brouillon), archiver ou restaurer un terrain")
    public TerrainVue changerStatut(@PathVariable long id, @RequestBody DemandeStatut d) {
        return gestion.changerStatut(id, d.statut());
    }

    @PutMapping("/terrains/{id}")
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Modifier un terrain")
    public TerrainVue modifierTerrain(@PathVariable long id, @Valid @RequestBody SaisieTerrain s) {
        return gestion.modifierTerrain(id, s);
    }

    @PutMapping("/horaires")
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Modifier les horaires d'ouverture")
    public List<HoraireVue> horaires(@RequestBody List<@Valid SaisieHoraire> horaires) {
        return gestion.modifierHoraires(horaires);
    }

    @GetMapping("/blocages")
    @PreAuthorize("hasAnyRole('GERANT', 'ACCUEIL')")
    @Operation(summary = "Blocages qui touchent la periode")
    public List<BlocageVue> blocages(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant debut,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fin) {
        return gestion.blocages(debut, fin);
    }

    @PostMapping("/blocages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GERANT', 'ACCUEIL')")
    @Operation(summary = "Fermer une periode a la reservation (un terrain ou tout le complexe)")
    public BlocageVue bloquer(@Valid @RequestBody SaisieBlocage s) {
        return gestion.bloquer(s);
    }

    @DeleteMapping("/blocages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('GERANT', 'ACCUEIL')")
    @Operation(summary = "Lever un blocage")
    public void debloquer(@PathVariable long id) {
        gestion.debloquer(id);
    }
}
