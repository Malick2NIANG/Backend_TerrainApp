package sn.terrainapp.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** Annonces : brouillon invisible, publication, slides image et video, droits. */
class AnnoncesApiIT extends IntegrationTest {

    private static final String CORPS = """
            {"titre":"%s","slides":[
              {"titre":"Nouveau terrain","message":"Ouvert ce week-end","mediaUrl":"/videos/demo/presentation.mp4","mediaType":"VIDEO"},
              {"titre":"Promo","mediaUrl":"/images/demo/foot-coin.jpg","mediaType":"IMAGE"},
              {"titre":"Sans visuel"}]}
            """;

    @Test
    void cycleDePublication() {
        String gerant = jeton("GERANT");
        String titre = unique("Annonce");
        Reponse cree = envoyer("POST", "/annonces", CORPS.formatted(titre), gerant);
        assertThat(cree.statut()).as(cree.corps()).isEqualTo(201);
        String id = cree.json().get("id").asString();
        assertThat(cree.json().get("statut").asString()).isEqualTo("BROUILLON");
        JsonNode slides = cree.json().get("slides");
        assertThat(slides.get(0).get("mediaType").asString()).isEqualTo("VIDEO");
        assertThat(slides.get(1).get("mediaType").asString()).isEqualTo("IMAGE");
        assertThat(slides.get(2).get("mediaType").asString()).isEqualTo("IMAGE");

        // Brouillon : invisible pour l'App Client, et demander les brouillons est refuse.
        assertThat(get("/annonces", null).corps()).doesNotContain(titre);
        assertThat(get("/annonces?statut=BROUILLON", null).statut()).isEqualTo(403);

        Reponse publiee = envoyer("PATCH", "/annonces/" + id + "/statut",
                "{\"statut\":\"ACTIF\",\"version\":" + cree.json().get("version").asLong() + "}", gerant);
        assertThat(publiee.statut()).as(publiee.corps()).isEqualTo(200);
        assertThat(publiee.json().get("publishedAt").isNull()).isFalse();
        assertThat(get("/annonces", null).corps()).contains(titre);

        // Version perimee : 409 plutot que d'ecraser.
        assertThat(envoyer("PUT", "/annonces/" + id, CORPS.formatted(titre).replace("\"slides\"", "\"version\":0,\"slides\""), gerant)
                .statut()).isEqualTo(409);
    }

    @Test
    void mediaCoherentAvecSonTypeEtDroits() {
        String gerant = jeton("GERANT");
        String image = """
                {"titre":"X","slides":[{"titre":"A","mediaUrl":"/images/demo/foot-coin.jpg","mediaType":"VIDEO"}]}""";
        assertThat(envoyer("POST", "/annonces", image, gerant).json().get("code").asString()).isEqualTo("MEDIA_INVALIDE");
        String script = """
                {"titre":"X","slides":[{"titre":"A","mediaUrl":"javascript:alert(1)","mediaType":"IMAGE"}]}""";
        assertThat(envoyer("POST", "/annonces", script, gerant).statut()).isEqualTo(400);
        assertThat(envoyer("POST", "/annonces", CORPS.formatted("Y"), jeton("ACCUEIL")).statut()).isEqualTo(403);
        assertThat(envoyer("POST", "/annonces", "{\"titre\":\"Vide\",\"slides\":[]}", gerant).statut()).isEqualTo(400);
    }
}
