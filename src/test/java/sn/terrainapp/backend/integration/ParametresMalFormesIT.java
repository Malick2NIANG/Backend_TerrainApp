package sn.terrainapp.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import sn.terrainapp.backend.commun.temps.Horloge;

/** Un parametre sans nom ("?=") donne une erreur 400 (requete mal formee) et non 500 (scan DAST). */
class ParametresMalFormesIT extends IntegrationTest {

    @Test
    void parametreSansNomRefuseEn400() {
        assertThat(get("/terrains/tendances", null).statut()).isEqualTo(200);
        assertThat(get("/terrains/tendances?=", null).statut()).isEqualTo(400);
        assertThat(get("/annonces?=", null).statut()).isEqualTo(400);
        assertThat(get("/disponibilites?=&date=" + LocalDate.now(Horloge.DAKAR), null).statut()).isEqualTo(400);
    }
}
