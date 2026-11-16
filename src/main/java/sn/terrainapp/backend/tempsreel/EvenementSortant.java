package sn.terrainapp.backend.tempsreel;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Outbox : evenement ecrit DANS la transaction de la reservation, publie
 * ensuite vers les postes du staff (SSE) par RelaisEvenements. Une reservation
 * enregistree produit donc toujours son evenement, meme si le serveur
 * s'arrete juste apres la validation.
 */
@Entity
@Table(name = "evenements_sortants")
public class EvenementSortant {

    public enum Type { RESERVATION_CONFIRMEE, STATUT_CHANGE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(name = "reservation_id", nullable = false)
    private long reservationId;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "publie_le")
    private Instant publieLe;

    protected EvenementSortant() {
    }

    public EvenementSortant(Type type, long reservationId, Instant creeLe) {
        this.type = type.name();
        this.reservationId = reservationId;
        this.creeLe = creeLe;
    }

    public void marquerPublie(Instant quand) {
        this.publieLe = quand;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public long getReservationId() {
        return reservationId;
    }
}
