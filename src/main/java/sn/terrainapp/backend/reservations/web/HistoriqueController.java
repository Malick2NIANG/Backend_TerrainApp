package sn.terrainapp.backend.reservations.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import sn.terrainapp.backend.reservations.application.HistoriqueReservations;
import sn.terrainapp.backend.reservations.application.HistoriqueReservations.Filtres;
import sn.terrainapp.backend.reservations.application.HistoriqueReservations.PageHistorique;
import sn.terrainapp.backend.reservations.domaine.StatutReservation;

@RestController
@Tag(name = "Reservations")
public class HistoriqueController {

    private final HistoriqueReservations historique;

    public HistoriqueController(HistoriqueReservations historique) {
        this.historique = historique;
    }

    @GetMapping("/historique/reservations")
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Historique des reservations (periode d'un an au plus, filtres, recette encaissee)")
    public ResponseEntity<PageHistorique> reservations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) Long terrainId,
            @RequestParam(required = false) StatutReservation statut,
            @RequestParam(required = false) String moyen,
            @RequestParam(required = false) String recherche,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int taille) {
        PageHistorique resultat = historique.rechercher(new Filtres(du, au, terrainId, statut, HistoriqueReservations.moyenDepuisCode(moyen), recherche), page, taille);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resultat);
    }
}
