package sn.terrainapp.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import sn.terrainapp.backend.commun.temps.Horloge;
import tools.jackson.databind.JsonNode;

/** Avis : apres le match seulement, un par reservation, note moyenne, moderation. */
class AvisApiIT extends IntegrationTest {

    private Reponse avecJeton(String methode, String chemin, String corps, String jeton) {
        return executer(java.net.http.HttpRequest.newBuilder(url(chemin))
                .header("Content-Type", "application/json").header("X-Jeton-Suivi", jeton)
                .method(methode, corps == null ? java.net.http.HttpRequest.BodyPublishers.noBody()
                        : java.net.http.HttpRequest.BodyPublishers.ofString(corps)).build());
    }

    @Test
    void noterApresLeMatch() {
        String gerant = jeton("GERANT");
        long sport = envoyer("POST", "/sports", "{\"nom\":\"" + unique("Sport") + "\",\"ordre\":1}", gerant).json().get("id").asLong();
        long terrain = envoyer("POST", "/terrains", """
                {"sportId":%d,"nom":"%s","capacite":10,"dureeCreneauMinutes":60,"tarifHeure":10000,"ordre":1}
                """.formatted(sport, unique("Terrain")), gerant).json().get("id").asLong();
        String debut = LocalDateTime.of(LocalDate.now(Horloge.DAKAR).plusDays(1), LocalTime.of(9, 0))
                .atZone(Horloge.DAKAR).toInstant().toString();
        Reponse r = executer(java.net.http.HttpRequest.newBuilder(url("/reservations"))
                .header("Content-Type", "application/json").header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("""
                        {"terrainId":%d,"debut":"%s","clientNom":"Awa Diop Ndiaye","clientTelephone":"77 222 33 44","moyenPaiement":"wave"}
                        """.formatted(terrain, debut))).build());
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        long id = r.json().get("reservation").get("id").asLong();
        String jeton = r.json().get("jetonSuivi").asString();
        String avis = "{\"note\":4,\"commentaire\":\"Bon gazon, vestiaires propres.\"}";

        // Avant le match : trop tot.
        assertThat(avecJeton("POST", "/reservations/" + id + "/avis", avis, jeton).json().get("code").asString())
                .isEqualTo("AVIS_TROP_TOT");

        // Ticket scanne a l'entree : match joue.
        String code = avecJeton("GET", "/reservations/" + id, null, jeton).json().get("codeTicket").asString();
        assertThat(envoyer("POST", "/tickets/scan", "{\"code\":\"" + code + "\"}", jeton("CONTROLEUR")).statut()).isEqualTo(200);

        assertThat(avecJeton("POST", "/reservations/" + id + "/avis", avis, "faux").statut()).isEqualTo(404);
        Reponse depose = avecJeton("POST", "/reservations/" + id + "/avis", avis, jeton);
        assertThat(depose.statut()).as(depose.corps()).isEqualTo(201);
        assertThat(depose.json().get("auteur").asString()).isEqualTo("Awa D.");
        assertThat(avecJeton("POST", "/reservations/" + id + "/avis", avis, jeton).json().get("code").asString())
                .isEqualTo("AVIS_DEJA_DONNE");
        assertThat(avecJeton("GET", "/reservations/" + id, null, jeton).json().get("avisNote").asInt()).isEqualTo(4);

        JsonNode fiche = get("/terrains/" + terrain, null).json();
        assertThat(fiche.get("noteMoyenne").asDouble()).isEqualTo(4.0);
        assertThat(fiche.get("nombreAvis").asLong()).isEqualTo(1);
        assertThat(get("/terrains/" + terrain + "/avis", null).json()).hasSize(1);

        // Moderation par le gerant.
        long avisId = depose.json().get("id").asLong();
        assertThat(envoyer("PATCH", "/avis/" + avisId, "{\"visible\":false}", jeton("ACCUEIL")).statut()).isEqualTo(403);
        assertThat(envoyer("PATCH", "/avis/" + avisId, "{\"visible\":false}", gerant).statut()).isEqualTo(200);
        assertThat(get("/terrains/" + terrain + "/avis", null).json()).isEmpty();
        assertThat(get("/terrains/" + terrain, null).json().get("nombreAvis").asLong()).isZero();
        assertThat(avecJeton("POST", "/reservations/" + id + "/avis", "{\"note\":9}", jeton).statut()).isEqualTo(400);
    }
}
