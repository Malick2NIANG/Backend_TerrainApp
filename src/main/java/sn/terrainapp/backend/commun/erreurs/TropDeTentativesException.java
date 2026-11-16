package sn.terrainapp.backend.commun.erreurs;

/** Trop d'essais (code PIN) : 429. */
public class TropDeTentativesException extends RuntimeException {

    public TropDeTentativesException(String message) {
        super(message);
    }
}
