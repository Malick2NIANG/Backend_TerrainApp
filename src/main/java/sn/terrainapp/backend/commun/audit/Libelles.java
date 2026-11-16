package sn.terrainapp.backend.commun.audit;

/** Formulations du journal d'audit, identiques a celles du Manager. */
public final class Libelles {

    private Libelles() {
    }

    /** 3500 -> "3 500 F CFA" (espace insecable fine, comme le Manager). */
    public static String fcfa(long montant) {
        return String.format(java.util.Locale.FRANCE, "%,d", montant).replace(' ', ' ').replace(' ', ' ')
                + " F CFA";
    }

    public static String guillemets(String texte) {
        return "« " + texte + " »";
    }
}
