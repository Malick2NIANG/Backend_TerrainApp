package sn.terrainapp.backend.annonces.domaine;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Cycle d'une annonce (memes actions que le Manager : Publier, Depublier,
 * Archiver, Restaurer). Seul ACTIF est visible dans l'App Client.
 */
public enum StatutAnnonce {
    BROUILLON,
    ACTIF,
    ARCHIVE;

    private static final Map<StatutAnnonce, Set<StatutAnnonce>> SUIVANTS = Map.of(
            BROUILLON, EnumSet.of(ACTIF, ARCHIVE),
            ACTIF, EnumSet.of(BROUILLON, ARCHIVE),
            ARCHIVE, EnumSet.of(BROUILLON));

    /** Rester dans le meme statut est toujours permis (requete rejouee). */
    public boolean peutPasserA(StatutAnnonce cible) {
        return this == cible || SUIVANTS.get(this).contains(cible);
    }
}
