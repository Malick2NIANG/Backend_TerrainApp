package sn.terrainapp.backend.annonces.domaine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import sn.terrainapp.backend.commun.erreurs.ConflitException;

/** Chaine d'une ou plusieurs slides, affichee en popup a l'ouverture de l'App Client. */
@Entity
@Table(name = "annonces")
public class Annonce {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String titre;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private StatutAnnonce statut = StatutAnnonce.BROUILLON;

    /**
     * Date de (re)publication. L'App Client s'en sert pour savoir si
     * l'annonce a deja ete vue : republier la fait reapparaitre.
     */
    @Column(name = "publiee_le")
    private Instant publieeLe;

    @OneToMany(mappedBy = "annonce", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<AnnonceSlide> slides = new ArrayList<>();

    @Version
    private long version;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    protected Annonce() {
    }

    public Annonce(String titre) {
        this.titre = titre.trim();
    }

    @PrePersist
    void avantCreation() {
        creeLe = Instant.now();
        modifieLe = creeLe;
    }

    @PreUpdate
    void avantModification() {
        modifieLe = Instant.now();
    }

    public void renommer(String titre) {
        this.titre = titre.trim();
    }

    /** Remplace toutes les slides, dans l'ordre donne. */
    public void remplacerSlides(List<SlideDonnees> nouvelles) {
        slides.clear();
        for (int i = 0; i < nouvelles.size(); i++) {
            SlideDonnees s = nouvelles.get(i);
            slides.add(new AnnonceSlide(this, i, s.titre(), s.message(), s.mediaUrl(), s.mediaType()));
        }
    }

    /** @return vrai si le statut a effectivement change */
    public boolean changerStatut(StatutAnnonce cible) {
        if (!statut.peutPasserA(cible)) {
            throw new ConflitException("TRANSITION_INTERDITE", "Ce changement de statut n'est pas possible.");
        }
        if (statut == cible) {
            return false;
        }
        statut = cible;
        if (cible == StatutAnnonce.ACTIF) {
            publieeLe = Instant.now();
        }
        return true;
    }

    /** Verrouillage optimiste : 409 si la fiche a change depuis sa lecture. */
    public void verifierVersion(Long versionLue) {
        if (versionLue != null && versionLue != version) {
            throw new ConflitException("VERSION_CONFLICT",
                    "Cette annonce a été modifiée entre-temps. Rechargez-la avant d'enregistrer.");
        }
    }

    public Long getId() { return id; }
    public String getTitre() { return titre; }
    public StatutAnnonce getStatut() { return statut; }
    public Instant getPublieeLe() { return publieeLe; }
    public List<AnnonceSlide> getSlides() { return slides; }
    public long getVersion() { return version; }
    public Instant getModifieLe() { return modifieLe; }

    public record SlideDonnees(String titre, String message, String mediaUrl, TypeMedia mediaType) {
    }
}
