package sn.terrainapp.backend.terrains.domaine;

/**
 * ACTIF : publie, reservable dans l'app. BROUILLON : en preparation ou
 * depublie (visible du personnel seulement). ARCHIVE : retire, hors du
 * planning ; restaurable en brouillon.
 */
public enum StatutTerrain {
    ACTIF, BROUILLON, ARCHIVE
}
