package sn.terrainapp.backend.reservations.application;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Jeton d'acces a une reservation, remis apres verification du code PIN
 * (reservation retrouvee depuis un autre telephone). Signe (HMAC-SHA256),
 * rien a stocker : "A.id.expiration.signature", valable 90 jours. Accepte
 * partout ou le jeton de suivi l'est (detail, ticket, relance du paiement).
 */
@Component
public class JetonsAcces {

    static final Duration VALIDITE = Duration.ofDays(90);
    private static final String ALGO = "HmacSHA256";

    private final byte[] cle;
    private final Clock horloge;

    public JetonsAcces(@Value("${app.jwt.secret}") String secret, Clock horloge) {
        // Cle derivee : le secret JWT n'est jamais reutilise tel quel.
        this.cle = hmac(secret.getBytes(StandardCharsets.UTF_8), "terrainapp-acces-reservation");
        this.horloge = horloge;
    }

    public String emettre(long reservationId) {
        long expiration = Instant.now(horloge).plus(VALIDITE).getEpochSecond();
        String charge = "A." + reservationId + "." + expiration;
        return charge + "." + signature(charge);
    }

    public boolean valide(long reservationId, String jeton) {
        if (jeton == null || !jeton.startsWith("A.")) {
            return false;
        }
        String[] parties = jeton.trim().split("\\.");
        if (parties.length != 4 || !parties[1].equals(String.valueOf(reservationId))) {
            return false;
        }
        long expiration;
        try {
            expiration = Long.parseLong(parties[2]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Instant.now(horloge).getEpochSecond() > expiration) {
            return false;
        }
        String attendue = signature("A." + parties[1] + "." + parties[2]);
        return MessageDigest.isEqual(attendue.getBytes(StandardCharsets.US_ASCII), parties[3].getBytes(StandardCharsets.US_ASCII));
    }

    /** Jeton de suivi d'origine (telephone qui a reserve) OU jeton d'acces par PIN. */
    public boolean autorise(long reservationId, String jeton, String empreinteJetonSuivi) {
        return JetonSuivi.correspond(jeton, empreinteJetonSuivi) || valide(reservationId, jeton);
    }

    private String signature(String charge) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(cle, charge));
    }

    private static byte[] hmac(byte[] cle, String donnees) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(cle, ALGO));
            return mac.doFinal(donnees.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
