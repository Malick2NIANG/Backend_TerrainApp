package sn.terrainapp.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import sn.terrainapp.backend.commun.temps.Horloge;
import sn.terrainapp.backend.complexe.ChargementDonneesInitiales;
import sn.terrainapp.backend.complexe.ConfigurationComplexe;
import tools.jackson.databind.JsonNode;

/**
 * Backend demarre avec un dossier de complexe (src/test/resources/complexe-test) :
 * identite servie par GET /marque, donnees initiales chargees une seule fois
 * (rejouable sans doublon), moyens de paiement et delai d'annulation du
 * complexe appliques, compte du personnel active par code a usage unique.
 */
class ComplexeIT extends IntegrationTest {

    private static final String CODE_ACTIVATION = "K7QX-M2PA-9RTE";

    @DynamicPropertySource
    static void dossier(DynamicPropertyRegistry r) {
        r.add("app.complexe.dossier", () -> "src/test/resources/complexe-test");
        String empreinte = new BCryptPasswordEncoder().encode(CODE_ACTIVATION);
        String json = "{\"gerant-config\":\"" + empreinte + "\"}";
        r.add("app.complexe.activations", () -> Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Autowired
    ChargementDonneesInitiales chargement;

    @Autowired
    ConfigurationComplexe configuration;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void identiteDuComplexeServieParLApi() {
        JsonNode m = get("/marque", null).json();
        assertThat(m.get("nom").asString()).isEqualTo("Arena Test");
        assertThat(m.get("personnalise").asBoolean()).isTrue();
        assertThat(m.get("ville").asString()).isEqualTo("Thiès");
        assertThat(m.get("couleurs").get("principale").asString()).isEqualTo("#1F4E9A");
        assertThat(m.get("couleurs").get("foncee").asString()).isEqualTo("#163A73");
        assertThat(m.get("couleurs").has("claire")).isFalse();
        assertThat(m.get("logoUrl").asString()).isEqualTo("/marque/logo.png");
        assertThat(m.get("gps").get("latitude").asDouble()).isEqualTo(14.791);
        assertThat(m.get("moyensPaiement").toString()).isEqualTo("[\"wave\",\"orange-money\",\"carte\"]");
        assertThat(m.get("regles").get("delaiAnnulationHeures").asInt()).isEqualTo(48);
        assertThat(m.get("regles").get("pasCreneauMinutes").asInt()).isEqualTo(30);
        assertThat(m.get("documents").get("nomSurTickets").asString()).isEqualTo("ARENA TEST");
        assertThat(m.get("reseauxSociaux").get("instagram").asString()).contains("arena.test");
    }

    @Test
    void donneesChargeesUneSeuleFois() {
        JsonNode terrain = terrainConfig();
        assertThat(terrain).as("terrain du dossier publie").isNotNull();
        assertThat(terrain.get("photos").get(0).asString()).isEqualTo("/marque/terrains/terrain-a.jpg");
        assertThat(terrain.get("dureesProposees").toString()).isEqualTo("[60,90]");

        int avant = compter("Terrain Config A");
        ChargementDonneesInitiales.Bilan bilan = chargement.charger(configuration.dossier().orElseThrow());
        assertThat(bilan.crees()).as("rien de nouveau au 2e passage").isZero();
        assertThat(bilan.dejaPresents()).isGreaterThanOrEqualTo(5);
        assertThat(compter("Terrain Config A")).isEqualTo(avant).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sports where nom = 'Foot 5 (config test)'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from annonces where titre = 'Ouverture test'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from staff_users where username = 'gerant-config'", Integer.class)).isEqualTo(1);
    }

    @Test
    void modificationDuGerantConserveeAuRechargement() {
        long id = terrainConfig().get("id").asLong();
        jdbc.update("update terrains set description = 'Modifie par le gerant' where id = ?", id);
        chargement.charger(configuration.dossier().orElseThrow());
        assertThat(jdbc.queryForObject("select description from terrains where id = ?", String.class, id))
                .isEqualTo("Modifie par le gerant");
    }

    @Test
    void compteActiveParCodeAUsageUnique() {
        Reponse connexion = envoyer("POST", "/auth/login",
                "{\"username\":\"gerant-config\",\"password\":\"" + CODE_ACTIVATION + "\"}", null);
        assertThat(connexion.statut()).isEqualTo(200);
        assertThat(connexion.json().get("mustChangePassword").asBoolean()).isTrue();
        assertThat(connexion.json().get("token").isNull()).isTrue();

        Reponse choix = envoyer("POST", "/auth/first-login-password", """
                {"username":"gerant-config","temporaryPassword":"%s","newPassword":"Nouveau-Mot-2026"}
                """.formatted(CODE_ACTIVATION), null);
        assertThat(choix.statut()).as(choix.corps()).isEqualTo(200);
        assertThat(choix.json().get("role").asString()).isEqualTo("GERANT");
        // Le code ne sert plus.
        assertThat(envoyer("POST", "/auth/login",
                "{\"username\":\"gerant-config\",\"password\":\"" + CODE_ACTIVATION + "\"}", null).statut()).isEqualTo(401);
        // Jamais de code en clair en base.
        assertThat(jdbc.queryForObject("select password_hash from staff_users where username = 'gerant-config'", String.class))
                .startsWith("$2").doesNotContain(CODE_ACTIVATION);
    }

    @Test
    void moyenDePaiementNonProposeRefuse() {
        long t = terrainConfig().get("id").asLong();
        Reponse r = reserver(t, a(LocalDate.now(Horloge.DAKAR).plusDays(5), 10, 0), "mixx-yas", null);
        assertThat(r.statut()).isEqualTo(400);
        assertThat(r.json().get("code").asString()).isEqualTo("MOYEN_PAIEMENT_INVALIDE");
        assertThat(r.json().get("message").asString()).isEqualTo("Payez avec Wave, Orange Money ou par carte.");
        assertThat(reserver(t, a(LocalDate.now(Horloge.DAKAR).plusDays(5), 11, 0), "wave", null).statut()).isEqualTo(201);
    }

    @Test
    void delaiDAnnulationDuComplexe() {
        long t = terrainConfig().get("id").asLong();
        // Demain : dans le delai de 48 h. L'accueil ne peut plus annuler, le gerant si.
        Reponse proche = reserver(t, a(LocalDate.now(Horloge.DAKAR).plusDays(1), 9, 0), "especes", jeton("ACCUEIL"));
        assertThat(proche.statut()).as(proche.corps()).isEqualTo(201);
        long id = proche.json().get("reservation").get("id").asLong();
        Reponse refus = envoyer("POST", "/reservations/" + id + "/annulation", "{\"motif\":\"Le client a demandé\"}", jeton("ACCUEIL"));
        assertThat(refus.statut()).isEqualTo(409);
        assertThat(refus.json().get("code").asString()).isEqualTo("DELAI_ANNULATION_DEPASSE");
        assertThat(envoyer("POST", "/reservations/" + id + "/annulation", "{\"motif\":\"Terrain inondé\"}", jeton("GERANT"))
                .statut()).isEqualTo(200);

        // Dans 5 jours : hors delai, l'accueil annule.
        Reponse loin = reserver(t, a(LocalDate.now(Horloge.DAKAR).plusDays(5), 9, 0), "especes", jeton("ACCUEIL"));
        long idLoin = loin.json().get("reservation").get("id").asLong();
        assertThat(envoyer("POST", "/reservations/" + idLoin + "/annulation", "{\"motif\":\"Le client a demandé\"}",
                jeton("ACCUEIL")).statut()).isEqualTo(200);
    }

    // ------------------------------------------------------------ outils

    private JsonNode terrainConfig() {
        for (JsonNode t : get("/terrains", null).json()) {
            if ("Terrain Config A".equals(t.get("nom").asString())) {
                return t;
            }
        }
        return null;
    }

    private int compter(String nom) {
        return jdbc.queryForObject("select count(*) from terrains where nom = ?", Integer.class, nom);
    }

    private static Instant a(LocalDate jour, int heure, int minute) {
        return LocalDateTime.of(jour, LocalTime.of(heure, minute)).atZone(Horloge.DAKAR).toInstant();
    }

    /** jeton null : reservation en ligne (App Client) ; sinon au comptoir. */
    private Reponse reserver(long terrain, Instant debut, String moyen, String jeton) {
        String corps = """
                {"terrainId":%d,"debut":"%s","clientNom":"Joueur Config","clientTelephone":"77 123 45 67","moyenPaiement":"%s"}
                """.formatted(terrain, debut, moyen);
        var b = java.net.http.HttpRequest.newBuilder(url(jeton == null ? "/reservations" : "/reservations/comptoir"))
                .header("Content-Type", "application/json").header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(corps));
        if (jeton != null) {
            b.header("Authorization", "Bearer " + jeton);
        }
        return executer(b.build());
    }
}
