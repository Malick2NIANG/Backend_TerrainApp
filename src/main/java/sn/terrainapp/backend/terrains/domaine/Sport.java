package sn.terrainapp.backend.terrains.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Sport propose par le complexe (Football 5, Padel, Basket...), defini par le gerant. */
@Entity
@Table(name = "sports")
public class Sport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nom;

    @Column(nullable = false)
    private int ordre;

    @Column(nullable = false)
    private boolean actif = true;

    @Version
    private long version;

    protected Sport() {
    }

    public Sport(String nom, int ordre) {
        modifier(nom, ordre, true);
    }

    public void modifier(String nom, int ordre, boolean actif) {
        this.nom = nom.trim();
        this.ordre = ordre;
        this.actif = actif;
    }

    public Long getId() { return id; }
    public String getNom() { return nom; }
    public int getOrdre() { return ordre; }
    public boolean isActif() { return actif; }
    public long getVersion() { return version; }
}
