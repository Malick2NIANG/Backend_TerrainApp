package sn.terrainapp.backend.terrains.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import sn.terrainapp.backend.commun.securite.UtilisateurCourant;
import sn.terrainapp.backend.commun.web.CacheHttp;
import sn.terrainapp.backend.terrains.application.Catalogue;
import sn.terrainapp.backend.terrains.application.Vues.DisponibilitesVue;
import sn.terrainapp.backend.terrains.application.Vues.HoraireVue;
import sn.terrainapp.backend.terrains.application.Vues.SportVue;
import sn.terrainapp.backend.terrains.application.Vues.TerrainVue;

/** Lectures publiques (App Client sans compte) ; le personnel voit aussi les elements inactifs. */
@RestController
@Tag(name = "Terrains")
public class CatalogueController {

    private final Catalogue catalogue;

    public CatalogueController(Catalogue catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping("/sports")
    @Operation(summary = "Sports proposes (actifs ; tous pour le personnel)")
    public ResponseEntity<List<SportVue>> sports() {
        return CacheHttp.lecture(catalogue.sports(personnel()));
    }

    @GetMapping("/terrains")
    @Operation(summary = "Terrains reservables (tous pour le personnel)")
    public ResponseEntity<List<TerrainVue>> terrains() {
        return CacheHttp.lecture(catalogue.terrains(personnel()));
    }

    @GetMapping("/terrains/tendances")
    @Operation(summary = "Terrains les plus reserves (30 derniers jours), pour l'accueil de l'App Client")
    public ResponseEntity<List<TerrainVue>> tendances(@RequestParam(defaultValue = "4") int nombre) {
        return CacheHttp.lecture(catalogue.tendances(nombre));
    }

    @GetMapping("/terrains/{id}")
    @Operation(summary = "Fiche d'un terrain")
    public ResponseEntity<TerrainVue> terrain(@PathVariable long id) {
        return CacheHttp.lecture(catalogue.terrain(id, personnel()));
    }

    @GetMapping("/horaires")
    @Operation(summary = "Horaires d'ouverture du complexe (1 = lundi ... 7 = dimanche)")
    public ResponseEntity<List<HoraireVue>> horaires() {
        return CacheHttp.lecture(catalogue.horaires());
    }

    @GetMapping("/disponibilites")
    @Operation(summary = "Creneaux d'un jour, pour tous les terrains ou un seul (jamais en cache)")
    public ResponseEntity<List<DisponibilitesVue>> disponibilites(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long terrainId) {
        return CacheHttp.sansCache(catalogue.disponibilites(date, terrainId, personnel()));
    }

    private static boolean personnel() {
        return UtilisateurCourant.role().isPresent();
    }
}
