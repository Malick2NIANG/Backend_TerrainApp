package sn.terrainapp.backend.reservations.application;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import sn.terrainapp.backend.paiements.domaine.MoyenPaiement;

/**
 * Demande de reservation d'un creneau. App Client : moyen en ligne (wave,
 * orange-money, mixx-yas). Comptoir : especes ou paiement mobile verifie
 * par l'accueil. pin : 4 chiffres choisis par le client de l'App (exige en
 * ligne), pour retrouver ses reservations depuis un autre telephone.
 */
public record SaisieReservation(@NotNull Long terrainId, @NotNull Instant debut,
                                @NotBlank @Size(max = 80) String clientNom,
                                @NotBlank @Size(max = 30) String clientTelephone,
                                @NotNull MoyenPaiement moyenPaiement,
                                @Pattern(regexp = "^\\d{4}$", message = "Le code PIN fait 4 chiffres.") String pin) {
}
