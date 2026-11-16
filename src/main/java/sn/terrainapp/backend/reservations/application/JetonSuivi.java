package sn.terrainapp.backend.reservations.application;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import sn.terrainapp.backend.commun.idempotence.Idempotence;

/**
 * Jeton de suivi d'une reservation APP : secret aleatoire (256 bits) remis une
 * seule fois au joueur, garde sur son telephone. Seule son empreinte SHA-256
 * est stockee. Sans lui, un numero de reservation ne donne acces a rien.
 */
public final class JetonSuivi {

    private static final SecureRandom ALEA = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private JetonSuivi() {
    }

    public static String generer() {
        byte[] octets = new byte[32];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    /** Reference lisible a donner au telephone (sans 0/O, 1/I). */
    public static String reference() {
        StringBuilder s = new StringBuilder("R");
        for (int i = 0; i < 6; i++) {
            s.append(ALPHABET[ALEA.nextInt(ALPHABET.length)]);
        }
        return s.toString();
    }

    /** Code du QR du ticket : aleatoire, impossible a deviner. */
    public static String codeTicket() {
        byte[] octets = new byte[20];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    public static String empreinte(String jeton) {
        return Idempotence.empreinte(jeton);
    }

    /** Comparaison a temps constant. */
    public static boolean correspond(String jeton, String empreinteStockee) {
        if (jeton == null || jeton.isBlank() || empreinteStockee == null) {
            return false;
        }
        return MessageDigest.isEqual(empreinte(jeton.trim()).getBytes(), empreinteStockee.getBytes());
    }
}
