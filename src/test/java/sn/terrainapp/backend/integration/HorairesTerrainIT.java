package sn.terrainapp.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sn.terrainapp.backend.commun.temps.Horloge;
import tools.jackson.databind.JsonNode;

/** Horaires propres a un terrain : ses creneaux suivent ses heures, pas celles du complexe. */
class HorairesTerrainIT extends IntegrationTest {

    @Test
    void creneauxDansLesHorairesDuTerrain() {
        String gerant = jeton("GERANT");
        long sport = envoyer("POST", "/sports", "{\"nom\":\"" + unique("Sport") + "\",\"ordre\":1}", gerant).json().get("id").asLong();
        StringBuilder horaires = new StringBuilder();
        for (int j = 1; j <= 7; j++) {
            horaires.append(j > 1 ? "," : "").append("{\"jour\":").append(j)
                    .append(",\"ouvert\":true,\"ouverture\":\"10:00\",\"fermeture\":\"12:00\"}");
        }
        Reponse t = envoyer("POST", "/terrains", """
                {"sportId":%d,"nom":"%s","capacite":4,"dureeCreneauMinutes":60,"tarifHeure":10000,"ordre":1,"horaires":[%s]}
                """.formatted(sport, unique("Padel"), horaires), gerant);
        assertThat(t.statut()).as(t.corps()).isEqualTo(201);
        assertThat(t.json().get("horaires")).hasSize(7);
        long id = t.json().get("id").asLong();

        LocalDate demain = LocalDate.now(Horloge.DAKAR).plusDays(1);
        Reponse dispo = get("/disponibilites?date=" + demain + "&terrainId=" + id, null);
        List<String> heures = new ArrayList<>();
        for (JsonNode c : dispo.json().get(0).get("creneaux")) {
            heures.add(c.get("debut").asString().substring(11, 16));
        }
        assertThat(heures).containsExactly("10:00", "11:00");

        // 6 jours seulement : refuse.
        Reponse incomplet = envoyer("PUT", "/terrains/" + id, """
                {"sportId":%d,"nom":"X","capacite":4,"dureeCreneauMinutes":60,"tarifHeure":10000,"ordre":1,
                 "horaires":[{"jour":1,"ouvert":true,"ouverture":"10:00","fermeture":"12:00"}]}
                """.formatted(sport), gerant);
        assertThat(incomplet.json().get("code").asString()).isEqualTo("HORAIRES_INCOMPLETS");
    }

    @Test
    void publierDepublierArchiverRestaurer() {
        String gerant = jeton("GERANT");
        long sport = envoyer("POST", "/sports", "{\"nom\":\"" + unique("Sport") + "\",\"ordre\":1}", gerant).json().get("id").asLong();
        Reponse t = envoyer("POST", "/terrains", """
                {"sportId":%d,"nom":"%s","capacite":4,"dureeCreneauMinutes":60,"tarifHeure":10000,"ordre":1}
                """.formatted(sport, unique("Terrain")), gerant);
        long id = t.json().get("id").asLong();
        assertThat(t.json().get("statut").asString()).isEqualTo("ACTIF");

        // Une reservation a venir empeche l'archivage, pas la depublication.
        String debut = java.time.LocalDateTime.of(LocalDate.now(Horloge.DAKAR).plusDays(2), java.time.LocalTime.of(10, 0))
                .atZone(Horloge.DAKAR).toInstant().toString();
        Reponse r = executer(java.net.http.HttpRequest.newBuilder(url("/reservations"))
                .header("Content-Type", "application/json").header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("""
                        {"terrainId":%d,"debut":"%s","clientNom":"X","clientTelephone":"77 600 00 01","moyenPaiement":"wave"}
                        """.formatted(id, debut))).build());
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        assertThat(envoyer("PATCH", "/terrains/" + id + "/statut", "{\"statut\":\"ARCHIVE\"}", gerant)
                .json().get("code").asString()).isEqualTo("TERRAIN_RESERVE");
        Reponse brouillon = envoyer("PATCH", "/terrains/" + id + "/statut", "{\"statut\":\"BROUILLON\"}", gerant);
        assertThat(brouillon.json().get("statut").asString()).isEqualTo("BROUILLON");
        assertThat(get("/terrains/" + id, null).statut()).isEqualTo(404);
        assertThat(envoyer("PATCH", "/terrains/" + id + "/statut", "{\"statut\":\"ACTIF\"}", gerant)
                .json().get("statut").asString()).isEqualTo("ACTIF");
        assertThat(envoyer("PATCH", "/terrains/" + id + "/statut", "{\"statut\":\"ACTIF\"}", jeton("ACCUEIL")).statut()).isEqualTo(403);
    }
}
