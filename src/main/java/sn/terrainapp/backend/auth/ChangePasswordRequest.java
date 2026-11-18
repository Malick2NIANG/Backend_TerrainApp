package sn.terrainapp.backend.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Changement de son propre mot de passe par un membre du personnel connecte. */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank
        @Size(min = 12, max = 128, message = "12 caractères minimum.")
        @Schema(description = "Nouveau mot de passe, 12 caracteres minimum")
        String newPassword
) {
}
