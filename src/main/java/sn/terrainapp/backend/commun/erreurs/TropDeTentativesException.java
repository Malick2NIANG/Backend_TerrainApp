package sn.terrainapp.backend.commun.erreurs;

/** Trop d'essais (code SMS) : 429. */
public class TropDeTentativesException extends RuntimeException {

    public TropDeTentativesException(String message) {
        super(message);
    }
}
