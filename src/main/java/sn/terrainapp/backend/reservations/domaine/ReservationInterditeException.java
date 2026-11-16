package sn.terrainapp.backend.reservations.domaine;

import sn.terrainapp.backend.commun.erreurs.ConflitException;

/** Changement d'etat impossible depuis l'etat actuel (409). */
public class ReservationInterditeException extends ConflitException {

    public ReservationInterditeException(String code, String message) {
        super(code, message);
    }
}
