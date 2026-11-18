package sn.terrainapp.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Changement de mot de passe : premiere connexion puis depuis le profil. */
class MotDePasseIT extends IntegrationTest {

    @Test
    void premiereConnexionPuisChangementDepuisLeProfil() {
        String identifiant = "mdp" + Long.toString(System.nanoTime(), 36);
        Reponse cree = envoyer("POST", "/staff-users", """
                {"firstName":"Awa","lastName":"Test","address":"Dakar","cni":"1234567890123","phone":"771234567",
                 "username":"%s","role":"ACCUEIL"}
                """.formatted(identifiant), jeton("GERANT"));
        assertThat(cree.statut()).as(cree.corps()).isIn(200, 201);
        String temporaire = cree.json().get("temporaryPassword").asString();

        // Premiere connexion : le mot de passe temporaire doit etre remplace.
        Reponse login = envoyer("POST", "/auth/login",
                "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(identifiant, temporaire), null);
        assertThat(login.json().get("mustChangePassword").asBoolean()).isTrue();
        Reponse premier = envoyer("POST", "/auth/first-login-password",
                "{\"username\":\"%s\",\"temporaryPassword\":\"%s\",\"newPassword\":\"Premier-Mdp-2026\"}"
                        .formatted(identifiant, temporaire), null);
        assertThat(premier.statut()).as(premier.corps()).isEqualTo(200);
        String session = premier.json().get("token").asString();

        // Depuis le profil : sans session, refuse ; mauvais actuel, 400 ; trop court, 400.
        String corps = "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}";
        assertThat(envoyer("POST", "/auth/change-password", corps.formatted("Premier-Mdp-2026", "Nouveau-Mdp-2026"), null).statut())
                .isIn(401, 403);
        assertThat(envoyer("POST", "/auth/change-password", corps.formatted("faux-mot-de-passe", "Nouveau-Mdp-2026"), session).statut())
                .isEqualTo(400);
        assertThat(envoyer("POST", "/auth/change-password", corps.formatted("Premier-Mdp-2026", "court"), session).statut())
                .isEqualTo(400);
        assertThat(envoyer("POST", "/auth/change-password", corps.formatted("Premier-Mdp-2026", "Premier-Mdp-2026"), session).statut())
                .isEqualTo(400);

        Reponse change = envoyer("POST", "/auth/change-password", corps.formatted("Premier-Mdp-2026", "Nouveau-Mdp-2026"), session);
        assertThat(change.statut()).as(change.corps()).isEqualTo(200);
        assertThat(change.json().get("token").asString()).isNotBlank();

        // L'ancien ne marche plus, le nouveau oui.
        String connexion = "{\"username\":\"%s\",\"password\":\"%s\"}";
        assertThat(envoyer("POST", "/auth/login", connexion.formatted(identifiant, "Premier-Mdp-2026"), null).statut()).isEqualTo(401);
        Reponse ok = envoyer("POST", "/auth/login", connexion.formatted(identifiant, "Nouveau-Mdp-2026"), null);
        assertThat(ok.statut()).isEqualTo(200);
        assertThat(ok.json().get("mustChangePassword").asBoolean()).isFalse();
    }
}
