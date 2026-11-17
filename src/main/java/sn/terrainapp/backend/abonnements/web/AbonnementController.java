package sn.terrainapp.backend.abonnements.web;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import sn.terrainapp.backend.abonnements.application.AbonnementVue;
import sn.terrainapp.backend.abonnements.application.GestionAbonnements;
import sn.terrainapp.backend.abonnements.application.GestionAbonnements.Apercu;
import sn.terrainapp.backend.abonnements.application.SaisieAbonnement;
import sn.terrainapp.backend.abonnements.domaine.Frequence;
import sn.terrainapp.backend.paiements.domaine.MoyenPaiement;
import sn.terrainapp.backend.reservations.application.ReservationVue;

@RestController
@Tag(name = "Abonnements")
@PreAuthorize("hasAnyRole('ACCUEIL', 'GERANT')")
public class AbonnementController {

    private final GestionAbonnements gestion;

    public AbonnementController(GestionAbonnements gestion) {
        this.gestion = gestion;
    }

    public record DemandeApercu(@NotNull Long terrainId, @NotNull Instant premiereSeance, @NotNull Frequence frequence,
                                int nombreSeances, java.util.Set<Integer> jours, java.util.List<java.time.LocalDate> dates) {
    }

    @PostMapping("/abonnements/apercu")
    @Operation(summary = "Seances prevues d'un abonnement : libres, ou pourquoi elles seront sautees")
    public Apercu apercu(@Valid @RequestBody DemandeApercu d) {
        return gestion.apercu(d.terrainId(), d.premiereSeance(),
                new GestionAbonnements.Regle(d.frequence(), d.nombreSeances(), d.jours(), d.dates()));
    }

    @PostMapping("/abonnements")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer un abonnement : les seances libres deviennent des reservations confirmees")
    public AbonnementVue creer(@Valid @RequestBody SaisieAbonnement saisie) {
        return gestion.creer(saisie);
    }

    @GetMapping("/abonnements")
    @Operation(summary = "Abonnements (actifs d'abord) avec leurs seances")
    public List<AbonnementVue> tous() {
        return gestion.tous();
    }

    @GetMapping("/abonnements/{id}")
    public AbonnementVue un(@PathVariable long id) {
        return gestion.un(id);
    }

    @PostMapping("/abonnements/{id}/resiliation")
    @Operation(summary = "Resilier : les seances a venir sont annulees")
    public AbonnementVue resilier(@PathVariable long id) {
        return gestion.resilier(id);
    }

    public record DemandeEncaissement(@NotNull MoyenPaiement moyenPaiement, Long especesRecues) {
    }

    @PostMapping("/reservations/{id}/encaissement")
    @Operation(summary = "Encaisser au comptoir une seance d'abonnement payable le jour J")
    public ReservationVue encaisser(@PathVariable long id, @Valid @RequestBody DemandeEncaissement d) {
        return gestion.encaisser(id, d.moyenPaiement(), d.especesRecues());
    }
}
