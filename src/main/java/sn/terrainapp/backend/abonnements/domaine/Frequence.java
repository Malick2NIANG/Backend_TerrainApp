package sn.terrainapp.backend.abonnements.domaine;

/**
 * HEBDOMADAIRE : meme jour, meme heure chaque semaine.
 * MENSUELLE : meme rang du jour dans le mois (ex. 1er samedi, ou dernier
 * samedi quand la premiere seance tombe la 5e semaine), meme heure.
 */
public enum Frequence {
    HEBDOMADAIRE, MENSUELLE
}
