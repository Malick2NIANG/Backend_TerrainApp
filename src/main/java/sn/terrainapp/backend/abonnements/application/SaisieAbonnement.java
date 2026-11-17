package sn.terrainapp.backend.abonnements.application;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.terrainapp.backend.abonnements.domaine.Frequence;
import sn.terrainapp.backend.abonnements.domaine.ModeReglement;
import sn.terrainapp.backend.paiements.domaine.MoyenPaiement;

/**
 * Abonnement saisi au comptoir. moyenPaiement : exige pour un reglement
 * d'avance (ignore pour un reglement a chaque seance). remise : FCFA, sur
 * le total des seances creees.
 */
public record SaisieAbonnement(@NotNull Long terrainId, @NotNull Instant premiereSeance, @NotNull Frequence frequence,
                               @Min(2) @Max(52) int nombreSeances,
                               @NotBlank @Size(max = 80) String clientNom,
                               @NotBlank @Size(max = 30) String clientTelephone,
                               @NotNull ModeReglement reglement, MoyenPaiement moyenPaiement,
                               @Min(0) long remise) {
}
