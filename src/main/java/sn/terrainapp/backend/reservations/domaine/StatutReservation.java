package sn.terrainapp.backend.reservations.domaine;

/**
 * EN_ATTENTE_PAIEMENT : creneau tenu le temps du paiement (puis EXPIREE) ;
 * CONFIRMEE : payee ou saisie au comptoir ; HONOREE : ticket scanne a
 * l'entree ; ANNULEE : par le client ou le personnel.
 */
public enum StatutReservation {
    EN_ATTENTE_PAIEMENT,
    CONFIRMEE,
    HONOREE,
    ANNULEE,
    EXPIREE;

    /** Ce statut occupe-t-il le creneau ? */
    public boolean occupe() {
        return this == EN_ATTENTE_PAIEMENT || this == CONFIRMEE || this == HONOREE;
    }
}
