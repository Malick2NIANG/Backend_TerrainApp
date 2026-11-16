package sn.terrainapp.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import sn.terrainapp.backend.commun.temps.Horloge;

/** PIN oublie : code de recuperation par SMS, puis nouveau PIN. */
class RecuperationPinIT extends IntegrationTest {

    private static final String TEL = "76 400 11 22";
    private static final String TEL_INTERNATIONAL = "+221764001122";

    private long reserverAvecPin(String pin) {
        String gerant = jeton("GERANT");
        long sport = envoyer("POST", "/sports", "{\"nom\":\"" + unique("Sport") + "\",\"ordre\":1}", gerant).json().get("id").asLong();
        long terrain = envoyer("POST", "/terrains", """
                {"sportId":%d,"nom":"%s","capacite":10,"dureeCreneauMinutes":60,"tarifHeure":10000,"ordre":1}
                """.formatted(sport, unique("Terrain")), gerant).json().get("id").asLong();
        String debut = LocalDateTime.of(LocalDate.now(Horloge.DAKAR).plusDays(3), LocalTime.of(11, 0))
                .atZone(Horloge.DAKAR).toInstant().toString();
        Reponse r = executer(java.net.http.HttpRequest.newBuilder(url("/reservations"))
                .header("Content-Type", "application/json").header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("""
                        {"terrainId":%d,"debut":"%s","clientNom":"Awa","clientTelephone":"%s","moyenPaiement":"wave","pin":"%s"}
                        """.formatted(terrain, debut, TEL, pin))).build());
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        return r.json().get("reservation").get("id").asLong();
    }

    private static String codeDuSms() {
        Matcher m = Pattern.compile("(\\d{6})").matcher(SmsDeTest.DERNIERS.getOrDefault(TEL_INTERNATIONAL, ""));
        assertThat(m.find()).as("SMS de recuperation recu").isTrue();
        return m.group(1);
    }

    @Test
    void nouveauPinGraceAuCodeRecuParSms() {
        long id = reserverAvecPin("8642");
        assertThat(envoyer("POST", "/reservations/pin-oublie", "{\"telephone\":\"" + TEL + "\"}", null).statut()).isEqualTo(202);
        String code = codeDuSms();

        String mauvais = code.equals("000000") ? "111111" : "000000";
        Reponse rate = envoyer("POST", "/reservations/pin-oublie/verifier",
                "{\"telephone\":\"" + TEL + "\",\"code\":\"" + mauvais + "\",\"nouveauPin\":\"1357\"}", null);
        assertThat(rate.json().get("code").asString()).isEqualTo("CODE_INCORRECT");

        Reponse ok = envoyer("POST", "/reservations/pin-oublie/verifier",
                "{\"telephone\":\"" + TEL + "\",\"code\":\"" + code + "\",\"nouveauPin\":\"1357\"}", null);
        assertThat(ok.statut()).as(ok.corps()).isEqualTo(200);
        boolean trouvee = false;
        for (var n : ok.json()) {
            trouvee |= n.get("reservation").get("id").asLong() == id;
        }
        assertThat(trouvee).isTrue();

        // Le code ne sert qu'une fois ; l'ancien PIN ne marche plus, le nouveau si.
        assertThat(envoyer("POST", "/reservations/pin-oublie/verifier",
                "{\"telephone\":\"" + TEL + "\",\"code\":\"" + code + "\",\"nouveauPin\":\"2468\"}", null)
                .json().get("code").asString()).isEqualTo("CODE_EXPIRE");
        assertThat(envoyer("POST", "/reservations/retrouver", "{\"telephone\":\"" + TEL + "\",\"pin\":\"8642\"}", null).json()).isEmpty();
        assertThat(envoyer("POST", "/reservations/retrouver", "{\"telephone\":\"" + TEL + "\",\"pin\":\"1357\"}", null).json()).isNotEmpty();
    }

    @Test
    void numeroInconnuMemeReponseSansSms() {
        String inconnu = "+221709998877";
        assertThat(envoyer("POST", "/reservations/pin-oublie", "{\"telephone\":\"70 999 88 77\"}", null).statut()).isEqualTo(202);
        assertThat(SmsDeTest.DERNIERS).doesNotContainKey(inconnu);
        // 3 codes par heure au plus.
        envoyer("POST", "/reservations/pin-oublie", "{\"telephone\":\"70 999 88 77\"}", null);
        envoyer("POST", "/reservations/pin-oublie", "{\"telephone\":\"70 999 88 77\"}", null);
        assertThat(envoyer("POST", "/reservations/pin-oublie", "{\"telephone\":\"70 999 88 77\"}", null).statut()).isEqualTo(429);
    }
}
