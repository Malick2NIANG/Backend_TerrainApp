package sn.terrainapp.backend.reservations.application;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import sn.terrainapp.backend.paiements.domaine.MoyenPaiement;
import sn.terrainapp.backend.reservations.domaine.Canal;
import sn.terrainapp.backend.reservations.domaine.Reservation;
import sn.terrainapp.backend.reservations.domaine.StatutReservation;

/**
 * Reservation telle qu'exposee. Trois niveaux :
 * - client avec jeton : tout, dont le code du ticket une fois confirmee ;
 * - reduite (historique par numero) : sans nom ni ticket ;
 * - personnel : tout sauf le code du ticket (il se scanne, il ne se lit pas).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationVue(long id, String reference, long terrainId, String terrain, String sport,
                             Instant debut, Instant fin, long montant, StatutReservation statut, Canal canal,
                             MoyenPaiement moyenPaiement, String clientNom, String clientTelephone,
                             String codeTicket, Instant scanneLe, Instant creeLe) {

    public static ReservationVue pourClient(Reservation r) {
        boolean ticket = r.getStatut() == StatutReservation.CONFIRMEE || r.getStatut() == StatutReservation.HONOREE;
        return construire(r, true, ticket ? r.getCodeTicket() : null);
    }

    public static ReservationVue reduite(Reservation r) {
        return construire(r, false, null);
    }

    public static ReservationVue pourPersonnel(Reservation r) {
        return construire(r, true, null);
    }

    private static ReservationVue construire(Reservation r, boolean identite, String code) {
        return new ReservationVue(r.getId(), r.getReference(), r.getTerrain().getId(), r.getTerrain().getNom(),
                r.getTerrain().getSport().getNom(), r.getDebut(), r.getFin(), r.getMontant(), r.getStatut(), r.getCanal(),
                r.getMoyenPaiement(), identite ? r.getClientNom() : null, identite ? r.getClientTelephone() : null,
                code, r.getScanneLe(), r.getCreeLe());
    }
}
