package sn.terrainapp.backend.reservations.application;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.terrainapp.backend.paiements.domaine.MoyenPaiement;

/**
 * Demande de reservation d'un creneau. App Client : moyen en ligne (wave,
 * orange-money, mixx-yas). Comptoir : especes ou paiement mobile verifie
 * par l'accueil. especesRecues : au comptoir en especes, somme donnee par le
 * client (la monnaie a rendre en est deduite), facultative. dureeMinutes :
 * l'une des durees proposees pour le terrain (absente : la plus courte).
 */
public record SaisieReservation(@NotNull Long terrainId, @NotNull Instant debut,
                                @NotBlank @Size(max = 80) String clientNom,
                                @NotBlank @Size(max = 30) String clientTelephone,
                                @NotNull MoyenPaiement moyenPaiement,
                                @jakarta.validation.constraints.Min(0) Long especesRecues,
                                @jakarta.validation.constraints.Min(30) @jakarta.validation.constraints.Max(240) Integer dureeMinutes) {
}
