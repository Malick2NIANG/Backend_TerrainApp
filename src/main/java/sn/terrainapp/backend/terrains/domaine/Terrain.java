package sn.terrainapp.backend.terrains.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
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

    /** Quartier ou site du terrain (recherche de l'App Client). */
    @Column(length = 120)
    private String localite;

    /** Position GPS (itineraire depuis l'App Client) : les deux, ou aucune. */
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(length = 60)
    private String surface;

    @Column(nullable = false)
    private int capacite;

    /** Liste separee par des virgules (Eclairage, Vestiaires...). */
    @Column(length = 500)
    private String equipements;

    /** Galerie de l'annonce, dans l'ordre d'affichage : la premiere est la couverture. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "terrain_photos", joinColumns = @JoinColumn(name = "terrain_id"))
    @OrderColumn(name = "ordre")
    @Column(name = "url", length = 500, nullable = false)
    @Fetch(FetchMode.SELECT)
    private List<String> photos = new ArrayList<>();

    @Column(name = "duree_creneau_minutes", nullable = false)
    private int dureeCreneauMinutes;

    @Column(name = "tarif_heure", nullable = false)
    private long tarifHeure;

    @Column(nullable = false)
    private boolean actif = true;

    /** Retire du complexe (hors planning) ; restaurable. */
    @Column(nullable = false)
    private boolean archive;

    @Column(nullable = false)
    private int ordre;

    @OneToMany(mappedBy = "terrain", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("debut")
    private List<PlageTarifaire> plages = new ArrayList<>();

    /** Horaires propres au terrain (7 jours) ; vide : ceux du complexe. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "terrain_horaires", joinColumns = @JoinColumn(name = "terrain_id"))
    @Fetch(FetchMode.SELECT)
    private java.util.Set<HoraireTerrain> horaires = new java.util.HashSet<>();

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

    public void modifier(Sport sport, String nom, String description, String localite, String surface, int capacite,
                         List<String> equipements, List<String> photos, int dureeCreneauMinutes, long tarifHeure,
                         boolean actif, int ordre) {
        this.sport = sport;
        this.nom = nom.trim();
        this.description = vide(description);
        this.localite = vide(localite);
        this.surface = vide(surface);
        this.capacite = capacite;
        this.equipements = equipements == null || equipements.isEmpty() ? null
                : String.join(",", equipements.stream().map(String::trim).filter(e -> !e.isEmpty()).toList());
        this.photos.clear();
        if (photos != null) {
            photos.stream().map(Terrain::vide).filter(p -> p != null).distinct().forEach(this.photos::add);
        }
        this.dureeCreneauMinutes = dureeCreneauMinutes;
        this.tarifHeure = tarifHeure;
        this.actif = actif && !archive;
        this.ordre = ordre;
    }

    public void changerStatut(StatutTerrain statut) {
        this.archive = statut == StatutTerrain.ARCHIVE;
        this.actif = statut == StatutTerrain.ACTIF;
    }

    public StatutTerrain getStatut() {
        return archive ? StatutTerrain.ARCHIVE : actif ? StatutTerrain.ACTIF : StatutTerrain.BROUILLON;
    }

    public boolean isArchive() { return archive; }

    public void placer(Double latitude, Double longitude) {
        boolean complet = latitude != null && longitude != null;
        this.latitude = complet ? BigDecimal.valueOf(latitude).setScale(6, RoundingMode.HALF_UP) : null;
        this.longitude = complet ? BigDecimal.valueOf(longitude).setScale(6, RoundingMode.HALF_UP) : null;
    }

    public void remplacerPlages(List<PlageTarifaire> nouvelles) {
        plages.clear();
        for (PlageTarifaire p : nouvelles) {
            p.rattacher(this);
            plages.add(p);
        }
    }

    /** Horaires propres (7 jours), ou liste vide pour reprendre ceux du complexe. */
    public void remplacerHoraires(List<HoraireTerrain> nouveaux) {
        horaires.clear();
        horaires.addAll(nouveaux);
    }

    public boolean aSesHoraires() {
        return !horaires.isEmpty();
    }

    /** Horaires propres de ce jour (ferme si le jour manque), quand le terrain a les siens. */
    public java.util.Optional<HoraireTerrain> horaireDu(java.time.DayOfWeek jour) {
        return horaires.stream().filter(h -> h.getJourSemaine() == jour).findFirst();
    }

    public List<HoraireTerrain> getHoraires() {
        return horaires.stream().sorted(java.util.Comparator.comparingInt(HoraireTerrain::getJour)).toList();
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
    public String getLocalite() { return localite; }
    public Double getLatitude() { return latitude == null ? null : latitude.doubleValue(); }
    public Double getLongitude() { return longitude == null ? null : longitude.doubleValue(); }
    public List<String> getPhotos() { return photos; }
    /** Photo de couverture (la premiere de la galerie), ou null. */
    public String getPhotoUrl() { return photos.isEmpty() ? null : photos.get(0); }
    public int getDureeCreneauMinutes() { return dureeCreneauMinutes; }
    public long getTarifHeure() { return tarifHeure; }
    public boolean isActif() { return actif; }
    public int getOrdre() { return ordre; }
    public List<PlageTarifaire> getPlages() { return plages; }
    public long getVersion() { return version; }
}
