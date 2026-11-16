package sn.terrainapp.backend.terrains.domaine;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Horaires du complexe pour un jour de la semaine. Fermeture <= ouverture :
 * le complexe ferme apres minuit (ex. 08:00 -> 01:00) ; ces creneaux de nuit
 * appartiennent au jour d'ouverture.
 */
@Entity
@Table(name = "horaires_ouverture")
public class HoraireOuverture {

    /** 1 = lundi ... 7 = dimanche. */
    @Id
    private int jour;

    @Column(nullable = false)
    private boolean ouvert;

    @Column(nullable = false)
    private LocalTime ouverture;

    @Column(nullable = false)
    private LocalTime fermeture;

    @Version
    private long version;

    protected HoraireOuverture() {
    }

    public void modifier(boolean ouvert, LocalTime ouverture, LocalTime fermeture) {
        this.ouvert = ouvert;
        this.ouverture = ouverture;
        this.fermeture = fermeture;
    }

    public boolean passeMinuit() {
        return !fermeture.isAfter(ouverture);
    }

    public DayOfWeek getJourSemaine() { return DayOfWeek.of(jour); }
    public boolean isOuvert() { return ouvert; }
    public LocalTime getOuverture() { return ouverture; }
    public LocalTime getFermeture() { return fermeture; }
}
