package sn.terrainapp.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import sn.terrainapp.backend.commun.temps.Horloge;
import tools.jackson.databind.JsonNode;

/** Historique des reservations du gerant : periode, filtres, recette, droits. */
class HistoriqueApiIT extends IntegrationTest {

    private long creerTerrain() {
        String gerant = jeton("GERANT");
        long sport = envoyer("POST", "/sports", "{\"nom\":\"" + unique("Sport") + "\",\"ordre\":1}", gerant).json().get("id").asLong();
        Reponse t = envoyer("POST", "/terrains", """
                {"sportId":%d,"nom":"%s","capacite":10,"dureeCreneauMinutes":60,"tarifHeure":10000,"ordre":1}
                """.formatted(sport, unique("Terrain")), gerant);
        assertThat(t.statut()).as(t.corps()).isEqualTo(201);
        return t.json().get("id").asLong();
    }

    private JsonNode comptoir(long terrain, LocalDate jour, int heure, String nom, String moyen) {
        Instant debut = LocalDateTime.of(jour, LocalTime.of(heure, 0)).atZone(Horloge.DAKAR).toInstant();
        Reponse r = executer(java.net.http.HttpRequest.newBuilder(url("/reservations/comptoir"))
                .header("Content-Type", "application/json").header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + jeton("ACCUEIL"))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("""
                        {"terrainId":%d,"debut":"%s","clientNom":"%s","clientTelephone":"77 400 00 0%d","moyenPaiement":"%s"}
                        """.formatted(terrain, debut, nom, heure % 10, moyen))).build());
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        // Reponse du comptoir : { reservation, jetonSuivi, paiement }.
        return r.json().get("reservation");
    }

    @Test
    void periodeFiltresEtRecette() {
        long t = creerTerrain();
        LocalDate jour = LocalDate.now(Horloge.DAKAR).plusDays(3);
        comptoir(t, jour, 10, "Histo Alpha", "especes");
        comptoir(t, jour, 11, "Histo Beta", "wave");
        long annulee = comptoir(t, jour, 12, "Histo Gamma", "wave").get("id").asLong();
        Reponse a = envoyer("POST", "/reservations/" + annulee + "/annulation", "{\"motif\":\"Pluie annoncée\"}", jeton("GERANT"));
        assertThat(a.statut()).as(a.corps()).isEqualTo(200);

        String base = "/historique/reservations?du=" + jour + "&au=" + jour + "&terrainId=" + t;
        assertThat(get(base, null).statut()).isEqualTo(401);
        assertThat(get(base, jeton("ACCUEIL")).statut()).isEqualTo(403);

        Reponse h = get(base, jeton("GERANT"));
        assertThat(h.statut()).as(h.corps()).isEqualTo(200);
        JsonNode j = h.json();
        assertThat(j.get("total").asLong()).isEqualTo(3);
        assertThat(j.get("parStatut").get("CONFIRMEE").asLong()).isEqualTo(2);
        assertThat(j.get("parStatut").get("ANNULEE").asLong()).isEqualTo(1);
        // Recette : les deux reservations payees, l'annulee exclue.
        assertThat(j.get("encaisse").asLong()).isEqualTo(20000);
        assertThat(j.get("encaisseParMoyen").get("especes").asLong()).isEqualTo(10000);
        assertThat(j.get("encaisseParMoyen").get("wave").asLong()).isEqualTo(10000);
        // Le plus recent d'abord.
        assertThat(j.get("elements").get(0).get("clientNom").asString()).isEqualTo("Histo Gamma");

        JsonNode wave = get(base + "&moyen=wave&statut=CONFIRMEE", jeton("GERANT")).json();
        assertThat(wave.get("total").asLong()).isEqualTo(1);
        assertThat(wave.get("elements").get(0).get("clientNom").asString()).isEqualTo("Histo Beta");

        JsonNode cherche = get(base + "&recherche=alpha&taille=1&page=0", jeton("GERANT")).json();
        assertThat(cherche.get("total").asLong()).isEqualTo(1);
        assertThat(cherche.get("elements")).hasSize(1);

        assertThat(get("/historique/reservations?du=2025-01-01&au=2026-06-01", jeton("GERANT")).statut()).isEqualTo(400);
        assertThat(get(base + "&moyen=cheque", jeton("GERANT")).statut()).isEqualTo(400);
    }
}
