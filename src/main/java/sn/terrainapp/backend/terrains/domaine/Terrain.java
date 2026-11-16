package sn.terrainapp.backend.terrains.domaine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Terrain reservable. Le prix d'un creneau vient du tarif horaire du
 * terrain, ou d'une plage tarifaire (heures pleines, week-end) qui couvre
 * le debut du creneau.
 */
@Entity
@Table(name = "terrains")
public class Terrain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sport_id")
    private Sport sport;

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(length = 1000)
    private String description;

    @Column(length = 60)
    private String surface;

    @Column(nullable = false)
    private int capacite;

    /** Liste separee par des virgules (Eclairage, Vestiaires...). */
    @Column(length = 500)
    private String equipements;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "duree_creneau_minutes", nullable = false)
    private int dureeCreneauMinutes;

    @Column(name = "tarif_heure", nullable = false)
    private long tarifHeure;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(nullable = false)
    private int ordre;

    @OneToMany(mappedBy = "terrain", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("debut")
    private List<PlageTarifaire> plages = new ArrayList<>();

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Version
    private long version;

    protected Terrain() {
    }

    public Terrain(Sport sport) {
        this.sport = sport;
    }

    @PrePersist
    void avantCreation() {
        creeLe = Instant.now();
    }

    public void modifier(Sport sport, String nom, String description, String surface, int capacite,
                         List<String> equipements, String photoUrl, int dureeCreneauMinutes, long tarifHeure,
                         boolean actif, int ordre) {
        this.sport = sport;
        this.nom = nom.trim();
        this.description = vide(description);
        this.surface = vide(surface);
        this.capacite = capacite;
        this.equipements = equipements == null || equipements.isEmpty() ? null
                : String.join(",", equipements.stream().map(String::trim).filter(e -> !e.isEmpty()).toList());
        this.photoUrl = vide(photoUrl);
        this.dureeCreneauMinutes = dureeCreneauMinutes;
        this.tarifHeure = tarifHeure;
        this.actif = actif;
        this.ordre = ordre;
    }

    public void remplacerPlages(List<PlageTarifaire> nouvelles) {
        plages.clear();
        for (PlageTarifaire p : nouvelles) {
            p.rattacher(this);
            plages.add(p);
        }
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public List<String> listeEquipements() {
        return equipements == null ? List.of() : List.of(equipements.split(","));
    }

    public Long getId() { return id; }
    public Sport getSport() { return sport; }
    public String getNom() { return nom; }
    public String getDescription() { return description; }
    public String getSurface() { return surface; }
    public int getCapacite() { return capacite; }
    public String getPhotoUrl() { return photoUrl; }
    public int getDureeCreneauMinutes() { return dureeCreneauMinutes; }
    public long getTarifHeure() { return tarifHeure; }
    public boolean isActif() { return actif; }
    public int getOrdre() { return ordre; }
    public List<PlageTarifaire> getPlages() { return plages; }
    public long getVersion() { return version; }
}
