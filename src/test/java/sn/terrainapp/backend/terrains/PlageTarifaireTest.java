package sn.terrainapp.backend.terrains;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import sn.terrainapp.backend.terrains.domaine.PlageTarifaire;

class PlageTarifaireTest {

    @Test
    void plageDeJournee() {
        PlageTarifaire p = new PlageTarifaire("Week-end", EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                LocalTime.of(8, 0), LocalTime.of(18, 0), 1);
        assertThat(p.couvre(DayOfWeek.SATURDAY, LocalTime.of(8, 0))).isTrue();
        assertThat(p.couvre(DayOfWeek.SATURDAY, LocalTime.of(18, 0))).isFalse();
        assertThat(p.couvre(DayOfWeek.FRIDAY, LocalTime.of(10, 0))).isFalse();
    }

    @Test
    void plageQuiPasseMinuitAppartientAuJourDeDebut() {
        // Vendredi soir seulement, 18:00 -> 01:00.
        PlageTarifaire p = new PlageTarifaire("Vendredi soir", EnumSet.of(DayOfWeek.FRIDAY),
                LocalTime.of(18, 0), LocalTime.of(1, 0), 1);
        assertThat(p.couvre(DayOfWeek.FRIDAY, LocalTime.of(23, 0))).isTrue();
        assertThat(p.couvre(DayOfWeek.SATURDAY, LocalTime.of(0, 0))).isTrue();
        assertThat(p.couvre(DayOfWeek.SATURDAY, LocalTime.of(1, 0))).isFalse();
        assertThat(p.couvre(DayOfWeek.FRIDAY, LocalTime.of(0, 30))).isFalse();
        assertThat(p.couvre(DayOfWeek.SATURDAY, LocalTime.of(19, 0))).isFalse();
    }
}
