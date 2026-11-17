package sn.terrainapp.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import sn.terrainapp.backend.commun.temps.Horloge;

/** Un parametre sans nom ("?=") est ignore au lieu de provoquer une erreur 500 (scan DAST). */
class ParametresSansNomIT extends IntegrationTest {

    @Test
    void parametreSansNomIgnore() {
        assertThat(get("/terrains/tendances?=", null).statut()).isEqualTo(200);
        assertThat(get("/annonces?=", null).statut()).isEqualTo(200);
        assertThat(get("/disponibilites?=&date=" + LocalDate.now(Horloge.DAKAR), null).statut()).isEqualTo(200);
    }
}
