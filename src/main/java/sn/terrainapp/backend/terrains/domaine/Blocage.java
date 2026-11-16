package sn.terrainapp.backend.terrains.domaine;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Periode fermee a la reservation (entretien, evenement). terrainId null = tout le complexe. */
@Entity
@Table(name = "blocages")
public class Blocage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "terrain_id")
    private Long terrainId;

    @Column(nullable = false)
    private Instant debut;

    @Column(nullable = false)
    private Instant fin;

    @Column(length = 200)
    private String motif;

    @Column(name = "cree_par", nullable = false)
    private String creePar;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    protected Blocage() {
    }

    public Blocage(Long terrainId, Instant debut, Instant fin, String motif, String creePar, Instant creeLe) {
        this.terrainId = terrainId;
        this.debut = debut;
        this.fin = fin;
        this.motif = motif == null || motif.isBlank() ? null : motif.trim();
        this.creePar = creePar;
        this.creeLe = creeLe;
    }

    public boolean chevauche(long terrain, Instant d, Instant f) {
        return (terrainId == null || terrainId == terrain) && debut.isBefore(f) && fin.isAfter(d);
    }

    public Long getId() { return id; }
    public Long getTerrainId() { return terrainId; }
    public Instant getDebut() { return debut; }
    public Instant getFin() { return fin; }
    public String getMotif() { return motif; }
}
