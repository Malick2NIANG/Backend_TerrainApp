package sn.terrainapp.backend.annonces.domaine;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "annonce_slides")
public class AnnonceSlide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "annonce_id", nullable = false)
    private Annonce annonce;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 80)
    private String titre;

    @Column(length = 300)
    private String message;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "media_type", nullable = false, length = 5)
    private TypeMedia mediaType = TypeMedia.IMAGE;

    protected AnnonceSlide() {
    }

    AnnonceSlide(Annonce annonce, int position, String titre, String message, String mediaUrl, TypeMedia mediaType) {
        this.annonce = annonce;
        this.position = position;
        this.titre = titre.trim();
        this.message = message == null || message.isBlank() ? null : message.trim();
        this.mediaUrl = mediaUrl == null || mediaUrl.isBlank() ? null : mediaUrl.trim();
        this.mediaType = this.mediaUrl == null || mediaType == null ? TypeMedia.IMAGE : mediaType;
    }

    public Long getId() { return id; }
    public int getPosition() { return position; }
    public String getTitre() { return titre; }
    public String getMessage() { return message; }
    public String getMediaUrl() { return mediaUrl; }
    public TypeMedia getMediaType() { return mediaType; }
}
