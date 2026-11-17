package sn.terrainapp.backend.terrains.domaine;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Horaires propres a un terrain pour un jour de la semaine (ex. le padel
 * ferme a 22:00, le foot a minuit). Fermeture <= ouverture : apres minuit.
 */
@Embeddable
public class HoraireTerrain {

    /** 1 = lundi ... 7 = dimanche. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int jour;

    @Column(nullable = false)
    private boolean ouvert;

    @Column(nullable = false)
    private LocalTime ouverture;

    @Column(nullable = false)
    private LocalTime fermeture;

    protected HoraireTerrain() {
    }

    public HoraireTerrain(int jour, boolean ouvert, LocalTime ouverture, LocalTime fermeture) {
        this.jour = jour;
        this.ouvert = ouvert;
        this.ouverture = ouverture;
        this.fermeture = fermeture;
    }

    public boolean passeMinuit() {
        return !fermeture.isAfter(ouverture);
    }

    public int getJour() { return jour; }
    public DayOfWeek getJourSemaine() { return DayOfWeek.of(jour); }
    public boolean isOuvert() { return ouvert; }
    public LocalTime getOuverture() { return ouverture; }
    public LocalTime getFermeture() { return fermeture; }

    @Override
    public boolean equals(Object o) {
        return o instanceof HoraireTerrain h && h.jour == jour;
    }

    @Override
    public int hashCode() {
        return Objects.hash(jour);
    }
}
