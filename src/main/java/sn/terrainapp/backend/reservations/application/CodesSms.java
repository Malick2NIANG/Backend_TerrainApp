package sn.terrainapp.backend.reservations.application;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Code a 6 chiffres envoye par SMS : seule son empreinte BCrypt est stockee. */
public final class CodesSms {

    private static final BCryptPasswordEncoder ENCODEUR = new BCryptPasswordEncoder(8);

    private CodesSms() {
    }

    public static String empreinte(String code) {
        return ENCODEUR.encode(code);
    }

    public static boolean correspond(String code, String empreinte) {
        return code != null && empreinte != null && code.matches("\\d{6}") && ENCODEUR.matches(code, empreinte);
    }
}
