package sn.terrainapp.backend.abonnements.domaine;

/**
 * HEBDOMADAIRE : meme jour, meme heure chaque semaine.
 * Avec des jours choisis : chaque semaine, ces jours-la (ex. mardi et jeudi).
 * MENSUELLE : meme rang du jour dans le mois (ex. 1er samedi, ou dernier
 * samedi quand la premiere seance tombe la 5e semaine), meme heure.
 * PERSONNALISEE : des dates choisies une a une, meme heure.
 */
public enum Frequence {
    HEBDOMADAIRE, MENSUELLE, PERSONNALISEE
}
