package sn.terrainapp.backend.commun.validation;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Adresse de video acceptee : une video deposee chez nous
 * (/api/v1/videos/...), une video de demonstration servie par les fronts
 * (/videos/...) ou une adresse https. Vide ou null = pas de video.
 */
public final class UrlVideo {

    private static final int LONGUEUR_MAX = 500;
    private static final Pattern VIDEO_LOCALE =
            Pattern.compile("^/api/v1/videos/[a-f0-9-]{36}\\.(mp4|webm)$");
    private static final Pattern VIDEO_DES_FRONTS =
            Pattern.compile("^/videos/[a-z0-9][a-z0-9/_-]*\\.(mp4|webm)$");
    /** Video du dossier du complexe (servie par les fronts sous /marque/). */
    private static final Pattern VIDEO_DU_COMPLEXE =
            Pattern.compile("^/marque/[a-z0-9][a-z0-9/_-]*\\.(mp4|webm)$");

    private UrlVideo() {
    }

    public static boolean estValide(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return true;
        }
        if (valeur.length() > LONGUEUR_MAX) {
            return false;
        }
        if (VIDEO_LOCALE.matcher(valeur).matches() || VIDEO_DES_FRONTS.matcher(valeur).matches()
                || VIDEO_DU_COMPLEXE.matcher(valeur).matches()) {
            return true;
        }
        try {
            URI uri = URI.create(valeur);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
