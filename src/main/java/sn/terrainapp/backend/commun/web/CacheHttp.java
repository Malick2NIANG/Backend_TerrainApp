package sn.terrainapp.backend.commun.web;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

import sn.terrainapp.backend.commun.securite.UtilisateurCourant;

/**
 * Lectures publiques (terrains, marque) : cache HTTP court (30 s) pour les
 * joueurs ; le personnel connecte voit toujours l'etat reel.
 * Les disponibilites ne passent jamais par ce cache.
 */
public final class CacheHttp {

    private static final CacheControl PUBLIC = CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic();

    private CacheHttp() {
    }

    public static <T> ResponseEntity<T> lecture(T corps) {
        return ResponseEntity.ok()
                .cacheControl(UtilisateurCourant.role().isPresent() ? CacheControl.noStore() : PUBLIC)
                .body(corps);
    }

    public static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corps);
    }
}
