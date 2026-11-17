package sn.terrainapp.backend.commun.diffusion;

/**
 * Donnees publiques modifiees (terrains, disponibilites). Publie par les services d'ecriture ; relaye APRES validation de
 * la transaction vers les App Client connectees (tempsreel.FluxPublic), qui
 * rechargent alors le sujet concerne.
 */
public record ChangementPublic(Sujet sujet) {

    public enum Sujet {
        /** Sports, terrains, tarifs, horaires, blocages. */
        TERRAINS("terrains"),
        /** Un creneau a ete pris ou libere. */
        DISPONIBILITES("disponibilites"),
        /** Annonces publiees, modifiees ou retirees par le gerant. */
        ANNONCES("annonces");

        private final String nom;

        Sujet(String nom) {
            this.nom = nom;
        }

        public String nom() {
            return nom;
        }
    }
}
