package sn.terrainapp.backend.terrains.domaine;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tarif horaire particulier : certains jours, de debut a fin (fin <= debut :
 * la plage passe minuit, ex. 18:00 -> 01:00). S'applique a un creneau quand
 * son DEBUT tombe dans la plage.
 */
@Entity
@Table(name = "plages_tarifaires")
public class PlageTarifaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terrain_id")
    private Terrain terrain;

    @Column(nullable = false, length = 60)
    private String libelle;

    /** Masque : bit 0 = lundi ... bit 6 = dimanche. */
    @Column(nullable = false)
    private int jours;

    @Column(nullable = false)
    private LocalTime debut;

    @Column(nullable = false)
    private LocalTime fin;

    @Column(name = "tarif_heure", nullable = false)
    private long tarifHeure;

    protected PlageTarifaire() {
    }

    public PlageTarifaire(String libelle, Set<DayOfWeek> jours, LocalTime debut, LocalTime fin, long tarifHeure) {
        this.libelle = libelle.trim();
        this.jours = masque(jours);
        this.debut = debut;
        this.fin = fin;
        this.tarifHeure = tarifHeure;
    }

    void rattacher(Terrain terrain) {
        this.terrain = terrain;
    }

    /** Vrai si un creneau commencant ce jour-la a cette heure releve de la plage. */
    public boolean couvre(DayOfWeek jour, LocalTime heure) {
        if (fin.isAfter(debut)) {
            return contientJour(jour) && !heure.isBefore(debut) && heure.isBefore(fin);
        }
        // Plage qui passe minuit : la partie apres minuit appartient au jour de debut de la plage.
        if (!heure.isBefore(debut)) {
            return contientJour(jour);
        }
        return heure.isBefore(fin) && contientJour(jour.minus(1));
    }

    private boolean contientJour(DayOfWeek jour) {
        return (jours & (1 << (jour.getValue() - 1))) != 0;
    }

    public static int masque(Set<DayOfWeek> jours) {
        int m = 0;
        for (DayOfWeek j : jours) {
            m |= 1 << (j.getValue() - 1);
        }
        return m;
    }

    public Set<DayOfWeek> listeJours() {
        Set<DayOfWeek> s = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek j : DayOfWeek.values()) {
            if (contientJour(j)) {
                s.add(j);
            }
        }
        return s;
    }

    public Long getId() { return id; }
    public String getLibelle() { return libelle; }
    public LocalTime getDebut() { return debut; }
    public LocalTime getFin() { return fin; }
    public long getTarifHeure() { return tarifHeure; }
}
