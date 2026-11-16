package sn.terrainapp.backend.reservations.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import sn.terrainapp.backend.commun.erreurs.TropDeTentativesException;

/**
 * Essais de code PIN par numero : au-dela de 5 echecs en 15 minutes, le
 * numero est bloque 15 minutes (un PIN a 4 chiffres ne resiste pas a des
 * essais en serie). En memoire : une instance par complexe.
 */
@Component
public class TentativesPin {

    static final int MAX_ECHECS = 5;
    static final Duration FENETRE = Duration.ofMinutes(15);

    private record Compteur(int echecs, Instant depuis) {
    }

    private final Map<String, Compteur> compteurs = new ConcurrentHashMap<>();
    private final Clock horloge;

    public TentativesPin(Clock horloge) {
        this.horloge = horloge;
    }

    public void verifierAutorise(String telephone) {
        Compteur c = actuel(telephone);
        if (c != null && c.echecs() >= MAX_ECHECS) {
            long minutes = Math.max(1, Duration.between(Instant.now(horloge), c.depuis().plus(FENETRE)).toMinutes() + 1);
            throw new TropDeTentativesException("Trop d'essais pour ce numéro. Réessayez dans " + minutes + " min.");
        }
    }

    public void echec(String telephone) {
        Instant maintenant = Instant.now(horloge);
        compteurs.merge(telephone, new Compteur(1, maintenant), (avant, x) ->
                avant.depuis().plus(FENETRE).isBefore(maintenant) ? x : new Compteur(avant.echecs() + 1, avant.depuis()));
    }

    public void reussite(String telephone) {
        compteurs.remove(telephone);
    }

    private Compteur actuel(String telephone) {
        Compteur c = compteurs.get(telephone);
        if (c != null && c.depuis().plus(FENETRE).isBefore(Instant.now(horloge))) {
            compteurs.remove(telephone, c);
            return null;
        }
        return c;
    }
}
