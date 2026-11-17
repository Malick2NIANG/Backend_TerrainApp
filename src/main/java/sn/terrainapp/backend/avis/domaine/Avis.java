package sn.terrainapp.backend.avis.domaine;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Avis d'un joueur sur le terrain qu'il a reserve et joue. */
@Entity
@Table(name = "avis")
public class Avis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private long reservationId;

    @Column(name = "terrain_id", nullable = false)
    private long terrainId;

    @Column(nullable = false)
    private int note;

    @Column(length = 500)
    private String commentaire;

    /** Prenom et initiale du nom ("Awa D.") : jamais le nom complet ni le telephone. */
    @Column(nullable = false, length = 60)
    private String auteur;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    protected Avis() {
    }

    public Avis(long reservationId, long terrainId, int note, String commentaire, String auteur) {
        this.reservationId = reservationId;
        this.terrainId = terrainId;
        this.note = note;
        this.commentaire = commentaire == null || commentaire.isBlank() ? null : commentaire.trim();
        this.auteur = auteur;
    }

    @PrePersist
    void avantCreation() {
        creeLe = Instant.now();
    }

    public void rendreVisible(boolean visible) {
        this.visible = visible;
    }

    /** "Awa Diop Ndiaye" -> "Awa D." ; "Moussa" -> "Moussa". */
    public static String auteurDe(String nomComplet) {
        String[] mots = String.valueOf(nomComplet).trim().split("\\s+");
        if (mots.length == 0 || mots[0].isBlank()) {
            return "Joueur";
        }
        String prenom = mots[0].length() > 30 ? mots[0].substring(0, 30) : mots[0];
        return mots.length > 1 ? prenom + " " + Character.toUpperCase(mots[1].charAt(0)) + "." : prenom;
    }

    public Long getId() { return id; }
    public long getReservationId() { return reservationId; }
    public long getTerrainId() { return terrainId; }
    public int getNote() { return note; }
    public String getCommentaire() { return commentaire; }
    public String getAuteur() { return auteur; }
    public boolean isVisible() { return visible; }
    public Instant getCreeLe() { return creeLe; }
}
