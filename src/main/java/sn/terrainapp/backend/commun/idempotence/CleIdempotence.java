package sn.terrainapp.backend.commun.idempotence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Cle Idempotency-Key deja vue, avec la reponse renvoyee (conservee 24 h). */
@Entity
@Table(name = "cles_idempotence")
public class CleIdempotence {

    @Id
    @Column(length = 100)
    private String cle;

    @Column(name = "empreinte_requete", nullable = false, length = 64)
    private String empreinteRequete;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(columnDefinition = "mediumtext")
    private String reponse;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    protected CleIdempotence() {
    }

    public CleIdempotence(String cle, String empreinteRequete, Instant creeLe) {
        this.cle = cle;
        this.empreinteRequete = empreinteRequete;
        this.creeLe = creeLe;
    }

    public void enregistrerReponse(long reservationId, String reponse) {
        this.reservationId = reservationId;
        this.reponse = reponse;
    }

    public String getCle() {
        return cle;
    }

    public String getEmpreinteRequete() {
        return empreinteRequete;
    }

    public String getReponse() {
        return reponse;
    }
}
