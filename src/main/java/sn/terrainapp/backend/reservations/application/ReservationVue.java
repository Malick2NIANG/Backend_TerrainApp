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
 * - personnel : tout sauf le code du ticket (il se scanne, il ne se lit pas).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationVue(long id, String reference, long terrainId, String terrain, String sport,
                             String localite, Double latitude, Double longitude,
                             Instant debut, Instant fin, long montant, StatutReservation statut, Canal canal,
                             MoyenPaiement moyenPaiement, String clientNom, String clientTelephone,
                             String codeTicket, Instant payeeLe, Instant scanneLe, Instant creeLe, Integer avisNote,
                             Long abonnementId, String motifAnnulation,
                             Long especesRecues, Long monnaieRendue) {

    /** Meme vue, avec la note donnee par le joueur (null : pas encore note). */
    public ReservationVue avecAvis(Integer note) {
        return new ReservationVue(id, reference, terrainId, terrain, sport, localite, latitude, longitude, debut, fin, montant,
                statut, canal, moyenPaiement, clientNom, clientTelephone, codeTicket, payeeLe, scanneLe, creeLe, note, abonnementId, motifAnnulation, especesRecues, monnaieRendue);
    }

    public static ReservationVue pourClient(Reservation r) {
        boolean ticket = r.getStatut() == StatutReservation.CONFIRMEE || r.getStatut() == StatutReservation.HONOREE;
        return construire(r, true, ticket ? r.getCodeTicket() : null);
    }

    public static ReservationVue pourPersonnel(Reservation r) {
        return construire(r, true, null);
    }

    private static ReservationVue construire(Reservation r, boolean identite, String code) {
        var t = r.getTerrain();
        return new ReservationVue(r.getId(), r.getReference(), t.getId(), t.getNom(), t.getSport().getNom(),
                t.getLocalite(), t.getLatitude(), t.getLongitude(),
                r.getDebut(), r.getFin(), r.getMontant(), r.getStatut(), r.getCanal(),
                r.getMoyenPaiement(), identite ? r.getClientNom() : null, identite ? r.getClientTelephone() : null,
                code, r.getPayeeLe(), r.getScanneLe(), r.getCreeLe(), null, r.getAbonnementId(), r.getMotifAnnulation(),
                r.getEspecesRecues(), r.getMonnaieRendue());
    }
}
