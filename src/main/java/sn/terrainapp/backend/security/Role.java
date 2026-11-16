package sn.terrainapp.backend.security;

/**
 * Roles du personnel du complexe (back-office). Les joueurs de l'App Client
 * n'ont pas de role : ils ne sont jamais authentifies (sans compte).
 */
public enum Role {
    /** Sports, terrains, horaires, tarifs, blocages, offres, statistiques. */
    GERANT,
    /** Planning, reservation au comptoir, encaissement, annulations. */
    ACCUEIL,
    /** Scan des tickets a l'entree. */
    CONTROLEUR,
    /** Comptes du personnel et journal des actions (compte unique). */
    ADMIN
}
