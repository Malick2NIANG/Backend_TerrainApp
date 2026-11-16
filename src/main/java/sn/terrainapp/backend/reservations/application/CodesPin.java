package sn.terrainapp.backend.reservations.application;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Code PIN du client (4 chiffres) : seule son empreinte BCrypt est stockee.
 * Cout reduit (8) : on compare un PIN a quelques dizaines de reservations
 * au plus, et les essais sont limites par numero (TentativesPin).
 */
public final class CodesPin {

    private static final BCryptPasswordEncoder ENCODEUR = new BCryptPasswordEncoder(8);

    private CodesPin() {
    }

    public static String empreinte(String pin) {
        return ENCODEUR.encode(pin.trim());
    }

    /** Code de recuperation (6 chiffres) envoye par SMS. */
    public static String empreinteCode(String code) {
        return ENCODEUR.encode(code);
    }

    public static boolean correspondCode(String code, String empreinte) {
        return code != null && empreinte != null && code.matches("\\d{6}") && ENCODEUR.matches(code, empreinte);
    }

    public static boolean correspond(String pin, String empreinte) {
        return pin != null && empreinte != null && pin.matches("\\d{4}") && ENCODEUR.matches(pin, empreinte);
    }
}
