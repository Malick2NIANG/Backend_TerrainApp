package sn.terrainapp.backend.abonnements.application;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import sn.terrainapp.backend.abonnements.domaine.Frequence;
import sn.terrainapp.backend.abonnements.domaine.ModeReglement;
import sn.terrainapp.backend.abonnements.domaine.StatutAbonnement;
import sn.terrainapp.backend.reservations.application.ReservationVue;

/** Abonnement avec ses seances et son avancement (pour le personnel). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AbonnementVue(long id, long terrainId, String terrain, String sport, String clientNom,
                            String clientTelephone, Frequence frequence, Instant premiereSeance, int nombreSeances,
                            ModeReglement reglement, long remise, StatutAbonnement statut, Instant resilieLe,
                            Instant creeLe, long montantTotal, int seancesJouees, int seancesAVenir,
                            int seancesAEncaisser, long resteAEncaisser, Instant prochaineSeance,
                            List<ReservationVue> seances, List<Integer> jours) {
}
