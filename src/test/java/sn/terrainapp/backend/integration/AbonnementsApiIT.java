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

/** Abonnements au comptoir : apercu, seances sautees, reglement d'avance ou par seance, resiliation. */
class AbonnementsApiIT extends IntegrationTest {

    private long creerTerrain() {
        String gerant = jeton("GERANT");
        long sport = envoyer("POST", "/sports", "{\"nom\":\"" + unique("Sport") + "\",\"ordre\":1}", gerant).json().get("id").asLong();
        Reponse t = envoyer("POST", "/terrains", """
                {"sportId":%d,"nom":"%s","capacite":10,"dureeCreneauMinutes":60,"tarifHeure":10000,"ordre":1}
                """.formatted(sport, unique("Terrain")), gerant);
        assertThat(t.statut()).as(t.corps()).isEqualTo(201);
        return t.json().get("id").asLong();
    }

    private static Instant a(LocalDate jour, int heure) {
        return LocalDateTime.of(jour, LocalTime.of(heure, 0)).atZone(Horloge.DAKAR).toInstant();
    }

    private static LocalDate dans(int jours) {
        return LocalDate.now(Horloge.DAKAR).plusDays(jours);
    }

    @Test
    void hebdomadairePayeDAvanceAvecSeancePriseSautee() {
        long t = creerTerrain();
        String accueil = jeton("ACCUEIL");
        Instant premiere = a(dans(2), 10);
        // La 2e semaine est deja prise par un joueur de l'App.
        Reponse prise = executer(java.net.http.HttpRequest.newBuilder(url("/reservations"))
                .header("Content-Type", "application/json").header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("""
                        {"terrainId":%d,"debut":"%s","clientNom":"Autre","clientTelephone":"77 900 00 01","moyenPaiement":"wave"}
                        """.formatted(t, a(dans(9), 10)))).build());
        assertThat(prise.statut()).as(prise.corps()).isEqualTo(201);

        String demande = """
                {"terrainId":%d,"premiereSeance":"%s","frequence":"HEBDOMADAIRE","nombreSeances":4}
                """.formatted(t, premiere);
        assertThat(envoyer("POST", "/abonnements/apercu", demande, null).statut()).isEqualTo(401);
        Reponse apercu = envoyer("POST", "/abonnements/apercu", demande, accueil);
        assertThat(apercu.statut()).as(apercu.corps()).isEqualTo(200);
        assertThat(apercu.json().get("disponibles").asInt()).isEqualTo(3);
        assertThat(apercu.json().get("total").asLong()).isEqualTo(30000);
        assertThat(apercu.json().get("seances").get(1).get("motif").asString()).isEqualTo("Déjà réservé");

        Reponse cree = envoyer("POST", "/abonnements", """
                {"terrainId":%d,"premiereSeance":"%s","frequence":"HEBDOMADAIRE","nombreSeances":4,
                 "clientNom":"Équipe du jeudi","clientTelephone":"78 111 22 33","reglement":"AVANCE",
                 "moyenPaiement":"especes","remise":3000}
                """.formatted(t, premiere), accueil);
        assertThat(cree.statut()).as(cree.corps()).isEqualTo(201);
        JsonNode a = cree.json();
        assertThat(a.get("montantTotal").asLong()).isEqualTo(27000);
        assertThat(a.get("seances")).hasSize(3);
        assertThat(a.get("seancesAEncaisser").asInt()).isZero();
        for (JsonNode s : a.get("seances")) {
            assertThat(s.get("statut").asString()).isEqualTo("CONFIRMEE");
            assertThat(s.get("payeeLe").isNull()).isFalse();
            assertThat(s.get("abonnementId").asLong()).isEqualTo(a.get("id").asLong());
        }
        // Les seances occupent le planning.
        Reponse dispo = get("/disponibilites?date=" + dans(2) + "&terrainId=" + t, null);
        boolean reserve = false;
        for (JsonNode c : dispo.json().get(0).get("creneaux")) {
            reserve |= Instant.parse(c.get("debut").asString()).equals(premiere) && "RESERVE".equals(c.get("etat").asString());
        }
        assertThat(reserve).as(dispo.corps()).isTrue();

        // Resiliation : les seances a venir sont annulees.
        Reponse resilie = envoyer("POST", "/abonnements/" + a.get("id").asLong() + "/resiliation", null, jeton("GERANT"));
        assertThat(resilie.statut()).as(resilie.corps()).isEqualTo(200);
        assertThat(resilie.json().get("statut").asString()).isEqualTo("RESILIE");
        assertThat(resilie.json().get("seancesAVenir").asInt()).isZero();
    }

    @Test
    void mensuelPayableAChaqueSeance() {
        long t = creerTerrain();
        String accueil = jeton("ACCUEIL");
        Reponse cree = envoyer("POST", "/abonnements", """
                {"terrainId":%d,"premiereSeance":"%s","frequence":"MENSUELLE","nombreSeances":3,
                 "clientNom":"Club Mermoz","clientTelephone":"77 333 44 55","reglement":"SEANCE","remise":0}
                """.formatted(t, a(dans(3), 15)), accueil);
        assertThat(cree.statut()).as(cree.corps()).isEqualTo(201);
        JsonNode a = cree.json();
        assertThat(a.get("seancesAEncaisser").asInt()).isEqualTo(3);
        assertThat(a.get("resteAEncaisser").asLong()).isEqualTo(30000);
        long seance = a.get("seances").get(0).get("id").asLong();
        assertThat(a.get("seances").get(0).has("payeeLe")).isFalse();

        Reponse encaisse = envoyer("POST", "/reservations/" + seance + "/encaissement", "{\"moyenPaiement\":\"wave\"}", accueil);
        assertThat(encaisse.statut()).as(encaisse.corps()).isEqualTo(200);
        assertThat(encaisse.json().get("payeeLe").isNull()).isFalse();
        assertThat(envoyer("POST", "/reservations/" + seance + "/encaissement", "{\"moyenPaiement\":\"wave\"}", accueil)
                .json().get("code").asString()).isEqualTo("DEJA_PAYEE");
        Reponse liste = get("/abonnements", accueil);
        boolean vu = false;
        for (JsonNode x : liste.json()) {
            if (x.get("id").asLong() == a.get("id").asLong()) {
                vu = true;
                assertThat(x.get("seancesAEncaisser").asInt()).isEqualTo(2);
            }
        }
        assertThat(vu).isTrue();
    }

    @Test
    void plusieursJoursParSemaineEtDatesAuChoix() {
        long t = creerTerrain();
        String accueil = jeton("ACCUEIL");
        LocalDate lundi = dans(7).with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        // Mardi et jeudi, 4 seances a partir du mardi.
        Reponse hebdo = envoyer("POST", "/abonnements/apercu", """
                {"terrainId":%d,"premiereSeance":"%s","frequence":"HEBDOMADAIRE","nombreSeances":4,"jours":[2,4]}
                """.formatted(t, a(lundi.plusDays(1), 18)), accueil);
        assertThat(hebdo.statut()).as(hebdo.corps()).isEqualTo(200);
        java.util.List<String> jours = new java.util.ArrayList<>();
        for (JsonNode s : hebdo.json().get("seances")) {
            jours.add(Instant.parse(s.get("debut").asString()).atZone(Horloge.DAKAR).getDayOfWeek().name());
        }
        assertThat(jours).containsExactly("TUESDAY", "THURSDAY", "TUESDAY", "THURSDAY");

        // Dates au choix : la 1re seance + 2 dates (doublon ignore).
        Reponse cree = envoyer("POST", "/abonnements", """
                {"terrainId":%d,"premiereSeance":"%s","frequence":"PERSONNALISEE","nombreSeances":3,
                 "dates":["%s","%s","%s"],"clientNom":"Tournoi des écoles","clientTelephone":"77 444 55 66",
                 "reglement":"SEANCE","remise":0}
                """.formatted(t, a(lundi, 10), lundi.plusDays(10), lundi.plusDays(3), lundi.plusDays(3)), accueil);
        assertThat(cree.statut()).as(cree.corps()).isEqualTo(201);
        assertThat(cree.json().get("seances")).hasSize(3);
        assertThat(cree.json().get("frequence").asString()).isEqualTo("PERSONNALISEE");
    }

    @Test
    void modifierProlongerSupprimer() {
        long t = creerTerrain();
        String accueil = jeton("ACCUEIL");
        Reponse cree = envoyer("POST", "/abonnements", """
                {"terrainId":%d,"premiereSeance":"%s","frequence":"HEBDOMADAIRE","nombreSeances":2,
                 "clientNom":"Equipe A","clientTelephone":"77 555 00 11","reglement":"SEANCE","remise":0}
                """.formatted(t, a(dans(4), 9)), accueil);
        long id = cree.json().get("id").asLong();

        Reponse client = envoyer("PUT", "/abonnements/" + id + "/client",
                "{\"clientNom\":\"Equipe Alpha\",\"clientTelephone\":\"78 555 00 11\"}", accueil);
        assertThat(client.statut()).as(client.corps()).isEqualTo(200);
        assertThat(client.json().get("seances").get(0).get("clientNom").asString()).isEqualTo("Equipe Alpha");

        Reponse plus = envoyer("POST", "/abonnements/" + id + "/prolongation", "{\"nombreSeances\":2}", accueil);
        assertThat(plus.statut()).as(plus.corps()).isEqualTo(200);
        assertThat(plus.json().get("seances")).hasSize(4);
        Instant derniere = Instant.parse(plus.json().get("seances").get(3).get("debut").asString());
        assertThat(derniere).isEqualTo(a(dans(4).plusWeeks(3), 9));

        // Rien de joue ni de paye : suppression possible, l'abonnement disparait.
        assertThat(envoyer("DELETE", "/abonnements/" + id, null, accueil).statut()).isEqualTo(204);
        boolean present = false;
        for (JsonNode x : get("/abonnements", accueil).json()) {
            present |= x.get("id").asLong() == id;
        }
        assertThat(present).isFalse();
    }
}
