package sn.terrainapp.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import sn.terrainapp.backend.commun.temps.Horloge;
import tools.jackson.databind.JsonNode;

/** Parcours complet : configuration par le gerant, reservation, paiement simule, ticket, planning. */
class ReservationsApiIT extends IntegrationTest {

    private static final String TEL = "77 123 45 67";
    private static final String PIN = "2468";

    // ------------------------------------------------------------ outils

    private long creerTerrain(long tarifHeure, int duree) {
        String gerant = jeton("GERANT");
        Reponse sport = envoyer("POST", "/sports", "{\"nom\":\"" + unique("Sport") + "\",\"ordre\":1}", gerant);
        assertThat(sport.statut()).isEqualTo(201);
        Reponse t = envoyer("POST", "/terrains", """
                {"sportId":%d,"nom":"%s","capacite":10,"equipements":["Éclairage"],"dureeCreneauMinutes":%d,
                 "tarifHeure":%d,"ordre":1,
                 "plages":[{"libelle":"Soirée","jours":[1,2,3,4,5,6,7],"debut":"18:00","fin":"01:00","tarifHeure":20000}]}
                """.formatted(sport.json().get("id").asLong(), unique("Terrain"), duree, tarifHeure), gerant);
        assertThat(t.statut()).as(t.corps()).isEqualTo(201);
        return t.json().get("id").asLong();
    }

    private static Instant a(LocalDate jour, int heure, int minute) {
        return LocalDateTime.of(jour, LocalTime.of(heure, minute)).atZone(Horloge.DAKAR).toInstant();
    }

    private static LocalDate dans(int jours) {
        return LocalDate.now(Horloge.DAKAR).plusDays(jours);
    }

    private Reponse reserver(long terrain, Instant debut, String moyen, String cle) {
        String corps = """
                {"terrainId":%d,"debut":"%s","clientNom":"Joueur Test","clientTelephone":"%s","moyenPaiement":"%s","pin":"%s"}
                """.formatted(terrain, debut, TEL, moyen, PIN);
        return executer(java.net.http.HttpRequest.newBuilder(url("/reservations"))
                .header("Content-Type", "application/json").header("Idempotency-Key", cle)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(corps)).build());
    }

    private Reponse reserver(long terrain, Instant debut) {
        return reserver(terrain, debut, "wave", UUID.randomUUID().toString());
    }

    private Reponse avecJeton(String chemin, String jeton) {
        return executer(java.net.http.HttpRequest.newBuilder(url(chemin)).header("X-Jeton-Suivi", jeton).GET().build());
    }

    private static JsonNode creneau(Reponse dispo, Instant debut) {
        for (JsonNode c : dispo.json().get(0).get("creneaux")) {
            if (Instant.parse(c.get("debut").asString()).equals(debut)) {
                return c;
            }
        }
        throw new AssertionError("creneau absent : " + debut);
    }

    // ------------------------------------------------------------ tests

    @Test
    void creneauxEtPrixSelonLesPlagesTarifaires() {
        long t = creerTerrain(10000, 60);
        LocalDate jour = dans(2);
        Reponse dispo = get("/disponibilites?date=" + jour + "&terrainId=" + t, null);
        assertThat(dispo.statut()).isEqualTo(200);
        assertThat(dispo.entete("Cache-Control")).contains("no-store");
        JsonNode matin = creneau(dispo, a(jour, 10, 0));
        assertThat(matin.get("prix").asLong()).isEqualTo(10000);
        assertThat(matin.get("etat").asString()).isEqualTo("LIBRE");
        assertThat(creneau(dispo, a(jour, 19, 0)).get("prix").asLong()).isEqualTo(20000);
        // Ouverture 08:00 : premier creneau a 08:00.
        assertThat(Instant.parse(dispo.json().get(0).get("creneaux").get(0).get("debut").asString()))
                .isEqualTo(a(jour, 8, 0));
    }

    @Test
    void creneauDe90MinutesAuProrata() {
        long t = creerTerrain(10000, 90);
        LocalDate jour = dans(2);
        Reponse dispo = get("/disponibilites?date=" + jour + "&terrainId=" + t, null);
        JsonNode c = creneau(dispo, a(jour, 9, 30));
        assertThat(c.get("prix").asLong()).isEqualTo(15000);
        assertThat(Instant.parse(c.get("fin").asString())).isEqualTo(a(jour, 11, 0));
    }

    @Test
    void reservationPayeeEtTicketReserveAuDetenteurDuJeton() {
        long t = creerTerrain(10000, 60);
        Instant debut = a(dans(1), 10, 0);
        Reponse r = reserver(t, debut);
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        JsonNode res = r.json().get("reservation");
        assertThat(res.get("statut").asString()).isEqualTo("CONFIRMEE");
        assertThat(res.get("montant").asLong()).isEqualTo(10000);
        assertThat(res.get("clientTelephone").asString()).isEqualTo("+221771234567");
        assertThat(res.get("reference").asString()).matches("R[A-Z2-9]{6}");
        assertThat(r.json().get("paiement").get("simule").asBoolean()).isTrue();
        String jeton = r.json().get("jetonSuivi").asString();
        long id = res.get("id").asLong();

        assertThat(get("/reservations/" + id, null).statut()).isEqualTo(404);
        assertThat(avecJeton("/reservations/" + id, "faux").statut()).isEqualTo(404);
        Reponse detail = avecJeton("/reservations/" + id, jeton);
        assertThat(detail.statut()).isEqualTo(200);
        assertThat(detail.json().get("codeTicket").asString()).isNotBlank();

        // Le creneau apparait pris.
        Reponse dispo = get("/disponibilites?date=" + dans(1) + "&terrainId=" + t, null);
        assertThat(creneau(dispo, debut).get("etat").asString()).isEqualTo("RESERVE");

        // Le numero seul ne donne plus rien : il faut le code PIN.
        assertThat(get("/reservations?telephone=771234567", null).statut()).isIn(401, 403, 404, 405);
        Reponse mauvais = envoyer("POST", "/reservations/retrouver", "{\"telephone\":\"771234567\",\"pin\":\"0000\"}", null);
        assertThat(mauvais.statut()).isEqualTo(200);
        assertThat(mauvais.json()).isEmpty();
        Reponse trouvees = envoyer("POST", "/reservations/retrouver", "{\"telephone\":\"77 123 45 67\",\"pin\":\"" + PIN + "\"}", null);
        assertThat(trouvees.statut()).as(trouvees.corps()).isEqualTo(200);
        JsonNode trouvee = null;
        for (JsonNode n : trouvees.json()) {
            if (n.get("reservation").get("id").asLong() == id) {
                trouvee = n;
            }
        }
        assertThat(trouvee).isNotNull();
        assertThat(trouvee.get("reservation").get("payeeLe").isNull()).isFalse();
        // Le jeton d'acces ouvre le detail et le ticket, comme le jeton de suivi.
        Reponse viaPin = avecJeton("/reservations/" + id, trouvee.get("jetonAcces").asString());
        assertThat(viaPin.statut()).isEqualTo(200);
        assertThat(viaPin.json().get("codeTicket").asString()).isEqualTo(detail.json().get("codeTicket").asString());
        assertThat(avecJeton("/reservations/" + (id + 1), trouvee.get("jetonAcces").asString()).statut()).isEqualTo(404);
    }

    @Test
    void codePinObligatoireEtEssaisLimites() {
        long t = creerTerrain(10000, 60);
        String sansPin = """
                {"terrainId":%d,"debut":"%s","clientNom":"X","clientTelephone":"%s","moyenPaiement":"wave"}
                """.formatted(t, a(dans(2), 10, 0), TEL);
        Reponse r = executer(java.net.http.HttpRequest.newBuilder(url("/reservations"))
                .header("Content-Type", "application/json").header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(sansPin)).build());
        assertThat(r.json().get("code").asString()).isEqualTo("PIN_REQUIS");

        // 5 mauvais codes : le numero est bloque, meme avec le bon code.
        String numero = "70 555 12 34";
        for (int i = 0; i < 5; i++) {
            assertThat(envoyer("POST", "/reservations/retrouver",
                    "{\"telephone\":\"" + numero + "\",\"pin\":\"111" + i + "\"}", null).statut()).isEqualTo(200);
        }
        Reponse bloque = envoyer("POST", "/reservations/retrouver", "{\"telephone\":\"" + numero + "\",\"pin\":\"1234\"}", null);
        assertThat(bloque.statut()).isEqualTo(429);
        assertThat(bloque.json().get("code").asString()).isEqualTo("TROP_D_ESSAIS");
        assertThat(envoyer("POST", "/reservations/retrouver", "{\"telephone\":\"771234567\",\"pin\":\"12\"}", null)
                .statut()).isEqualTo(400);
    }

    @Test
    void memeCleIdempotenceRejoueLaPremiereReponse() {
        long t = creerTerrain(10000, 60);
        Instant debut = a(dans(1), 11, 0);
        String cle = UUID.randomUUID().toString();
        Reponse premiere = reserver(t, debut, "wave", cle);
        Reponse seconde = reserver(t, debut, "wave", cle);
        assertThat(premiere.statut()).isEqualTo(201);
        assertThat(seconde.statut()).isEqualTo(201);
        assertThat(seconde.entete("Idempotency-Replayed")).isEqualTo("true");
        assertThat(seconde.json().get("reservation").get("id").asLong())
                .isEqualTo(premiere.json().get("reservation").get("id").asLong());
    }

    @Test
    void jamaisDeDoubleReservationMemeEnSimultane() throws Exception {
        long t = creerTerrain(10000, 60);
        Instant debut = a(dans(1), 12, 0);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> essais = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                essais.add(() -> reserver(t, debut).statut());
            }
            List<Integer> statuts = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(essais)) {
                statuts.add(f.get());
            }
            assertThat(statuts).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuts).filteredOn(s -> s == 409).hasSize(5);
        } finally {
            pool.shutdown();
        }
        Reponse encore = reserver(t, debut);
        assertThat(encore.statut()).isEqualTo(409);
        assertThat(encore.json().get("code").asString()).isEqualTo("CRENEAU_PRIS");
    }

    @Test
    void demandesInvalides() {
        long t = creerTerrain(10000, 60);
        assertThat(reserver(t, a(dans(1), 10, 30)).json().get("code").asString()).isEqualTo("CRENEAU_INVALIDE");
        assertThat(reserver(t, a(dans(1), 7, 0)).json().get("code").asString()).isEqualTo("CRENEAU_INVALIDE");
        assertThat(reserver(t, a(dans(1), 13, 0), "especes", UUID.randomUUID().toString()).json().get("code").asString())
                .isEqualTo("MOYEN_PAIEMENT_INVALIDE");
        assertThat(reserver(t, a(dans(-1), 10, 0)).statut()).isEqualTo(409);
        // Sans Idempotency-Key : refuse.
        Reponse sansCle = envoyer("POST", "/reservations", """
                {"terrainId":%d,"debut":"%s","clientNom":"X","clientTelephone":"%s","moyenPaiement":"wave"}
                """.formatted(t, a(dans(1), 14, 0), TEL), null);
        assertThat(sansCle.statut()).isEqualTo(400);
        assertThat(get("/disponibilites?date=" + dans(-1), null).statut()).isEqualTo(400);
    }

    @Test
    void blocageFermeLeCreneau() {
        long t = creerTerrain(10000, 60);
        Instant debut = a(dans(1), 15, 0);
        Reponse b = envoyer("POST", "/blocages", """
                {"terrainId":%d,"debut":"%s","fin":"%s","motif":"Entretien"}
                """.formatted(t, debut, debut.plusSeconds(7200)), jeton("ACCUEIL"));
        assertThat(b.statut()).as(b.corps()).isEqualTo(201);
        assertThat(creneau(get("/disponibilites?date=" + dans(1) + "&terrainId=" + t, null), debut)
                .get("etat").asString()).isEqualTo("BLOQUE");
        Reponse r = reserver(t, debut);
        assertThat(r.statut()).isEqualTo(409);
        assertThat(r.json().get("code").asString()).isEqualTo("CRENEAU_BLOQUE");
        // Levee du blocage : le creneau redevient reservable.
        assertThat(envoyer("DELETE", "/blocages/" + b.json().get("id").asLong(), null, jeton("GERANT")).statut())
                .isEqualTo(204);
        assertThat(reserver(t, debut).statut()).isEqualTo(201);
    }

    @Test
    void comptoirAvecCodePinRetrouvableDansLApp() {
        long t = creerTerrain(10000, 60);
        String corps = """
                {"terrainId":%d,"debut":"%s","clientNom":"Fatou Sarr","clientTelephone":"78 555 66 77","moyenPaiement":"especes","pin":"5173"}
                """.formatted(t, a(dans(4), 15, 0));
        Reponse r = executer(java.net.http.HttpRequest.newBuilder(url("/reservations/comptoir"))
                .header("Content-Type", "application/json").header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + jeton("ACCUEIL"))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(corps)).build());
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        Reponse trouvees = envoyer("POST", "/reservations/retrouver", "{\"telephone\":\"785556677\",\"pin\":\"5173\"}", null);
        assertThat(trouvees.json()).hasSize(1);
        assertThat(trouvees.json().get(0).get("reservation").get("codeTicket").asString()).isNotBlank();
        assertThat(trouvees.json().get(0).get("reservation").get("payeeLe").isNull()).isFalse();
    }

    @Test
    void comptoirPlanningAnnulationEtScan() {
        long t = creerTerrain(10000, 60);
        LocalDate jour = dans(1);
        Instant debut = a(jour, 16, 0);
        String corps = """
                {"terrainId":%d,"debut":"%s","clientNom":"Client Comptoir","clientTelephone":"%s","moyenPaiement":"especes"}
                """.formatted(t, debut, TEL);
        // Public : interdit.
        assertThat(envoyer("POST", "/reservations/comptoir", corps, null).statut()).isEqualTo(401);
        Reponse comptoir = executer(java.net.http.HttpRequest.newBuilder(url("/reservations/comptoir"))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + jeton("ACCUEIL"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(corps)).build());
        assertThat(comptoir.statut()).as(comptoir.corps()).isEqualTo(201);
        JsonNode res = comptoir.json().get("reservation");
        assertThat(res.get("statut").asString()).isEqualTo("CONFIRMEE");
        assertThat(res.get("canal").asString()).isEqualTo("COMPTOIR");
        assertThat(comptoir.json().has("jetonSuivi")).isFalse();

        // Planning du jour : la reservation y est, sans code de ticket.
        Reponse planning = get("/planning?date=" + jour, jeton("ACCUEIL"));
        assertThat(planning.statut()).isEqualTo(200);
        assertThat(planning.corps()).contains(res.get("reference").asString()).doesNotContain("codeTicket");
        assertThat(get("/planning?date=" + jour, null).statut()).isEqualTo(401);

        // Reservation en ligne puis scan a l'entree (marge de test : 2 jours).
        Reponse enLigne = reserver(t, a(jour, 17, 0));
        String jeton = enLigne.json().get("jetonSuivi").asString();
        String code = avecJeton("/reservations/" + enLigne.json().get("reservation").get("id").asLong(), jeton)
                .json().get("codeTicket").asString();
        String controleur = jeton("CONTROLEUR");
        Reponse scan = envoyer("POST", "/tickets/scan", "{\"code\":\"" + code + "\"}", controleur);
        assertThat(scan.json().get("verdict").asString()).isEqualTo("VALIDE");
        assertThat(scan.json().get("reservation").get("statut").asString()).isEqualTo("HONOREE");
        assertThat(envoyer("POST", "/tickets/scan", "{\"code\":\"" + code + "\"}", controleur)
                .json().get("verdict").asString()).isEqualTo("DEJA_UTILISE");
        assertThat(envoyer("POST", "/tickets/scan", "{\"code\":\"inconnu\"}", controleur)
                .json().get("verdict").asString()).isEqualTo("INCONNU");

        // Annulation par l'accueil : creneau libere.
        long id = res.get("id").asLong();
        assertThat(envoyer("POST", "/reservations/" + id + "/annulation", null, controleur).statut()).isEqualTo(403);
        Reponse annul = envoyer("POST", "/reservations/" + id + "/annulation", null, jeton("ACCUEIL"));
        assertThat(annul.json().get("statut").asString()).isEqualTo("ANNULEE");
        assertThat(reserver(t, debut).statut()).isEqualTo(201);
    }

    @Test
    void scanParReferenceEnSecours() {
        long t = creerTerrain(10000, 60);
        Reponse r = reserver(t, a(dans(1), 18, 0));
        String reference = r.json().get("reservation").get("reference").asString();
        Reponse scan = envoyer("POST", "/tickets/scan", "{\"code\":\"" + reference.toLowerCase() + "\"}", jeton("ACCUEIL"));
        assertThat(scan.json().get("verdict").asString()).isEqualTo("VALIDE");
    }

    @Test
    void ticketTropTot() {
        long t = creerTerrain(10000, 60);
        Reponse r = reserver(t, a(dans(5), 10, 0));
        String code = avecJeton("/reservations/" + r.json().get("reservation").get("id").asLong(),
                r.json().get("jetonSuivi").asString()).json().get("codeTicket").asString();
        Reponse scan = envoyer("POST", "/tickets/scan", "{\"code\":\"" + code + "\"}", jeton("CONTROLEUR"));
        assertThat(scan.json().get("verdict").asString()).isEqualTo("TROP_TOT");
    }

    @Test
    void annonceAvecGalerieEtLocalite() {
        String gerant = jeton("GERANT");
        long sport = envoyer("POST", "/sports", "{\"nom\":\"" + unique("Sport") + "\",\"ordre\":1}", gerant).json().get("id").asLong();
        String corps = """
                {"sportId":%d,"nom":"%s","localite":"Liberté 6, Dakar","capacite":10,"dureeCreneauMinutes":60,
                 "tarifHeure":10000,"ordre":1,"photos":%s}
                """;
        String nom = unique("Terrain");
        Reponse cree = envoyer("POST", "/terrains", corps.formatted(sport, nom,
                "[\"/images/demo/foot-coin.jpg\",\"https://exemple.sn/terrain.jpg\",\"/videos/demo/presentation.mp4\"]"), gerant);
        assertThat(cree.statut()).as(cree.corps()).isEqualTo(201);
        long id = cree.json().get("id").asLong();

        JsonNode lu = get("/terrains/" + id, null).json();
        assertThat(lu.get("localite").asString()).isEqualTo("Liberté 6, Dakar");
        assertThat(lu.get("photos")).hasSize(3);
        assertThat(lu.get("photoUrl").asString()).isEqualTo("/images/demo/foot-coin.jpg");

        // Nouvel ordre : la couverture change.
        Reponse modifie = envoyer("PUT", "/terrains/" + id, corps.formatted(sport, nom,
                "[\"https://exemple.sn/terrain.jpg\",\"/images/demo/foot-coin.jpg\"]"), gerant);
        assertThat(modifie.statut()).as(modifie.corps()).isEqualTo(200);
        assertThat(modifie.json().get("photoUrl").asString()).isEqualTo("https://exemple.sn/terrain.jpg");
        assertThat(modifie.json().get("photos").get(1).asString()).isEqualTo("/images/demo/foot-coin.jpg");

        // Adresse refusee, et 10 photos au plus.
        assertThat(envoyer("PUT", "/terrains/" + id, corps.formatted(sport, nom, "[\"javascript:alert(1)\"]"), gerant)
                .statut()).isEqualTo(400);
        String onze = "[" + String.join(",", java.util.Collections.nCopies(11, "\"/images/demo/foot-coin.jpg\"")) + "]";
        assertThat(envoyer("PUT", "/terrains/" + id, corps.formatted(sport, nom, onze), gerant).statut()).isEqualTo(400);
    }

    @Test
    void seulLeGerantConfigureLesTerrains() {
        assertThat(envoyer("POST", "/sports", "{\"nom\":\"X\",\"ordre\":1}", jeton("ACCUEIL")).statut()).isEqualTo(403);
        assertThat(envoyer("POST", "/sports", "{\"nom\":\"X\",\"ordre\":1}", null).statut()).isEqualTo(401);
        assertThat(get("/terrains", null).statut()).isEqualTo(200);
        assertThat(get("/marque", null).json().get("nom").asString()).isNotBlank();
        assertThat(get("/horaires", null).json()).hasSize(7);
        Reponse tendances = get("/terrains/tendances?nombre=3", null);
        assertThat(tendances.statut()).isEqualTo(200);
        assertThat(tendances.json().size()).isLessThanOrEqualTo(3);
    }
}
