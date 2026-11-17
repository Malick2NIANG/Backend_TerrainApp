package sn.terrainapp.backend.abonnements.domaine;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import sn.terrainapp.backend.reservations.domaine.ReservationInterditeException;
import sn.terrainapp.backend.terrains.domaine.Terrain;

/**
 * Creneau recurrent vendu au comptoir : un client, un terrain, une heure,
 * une frequence et un nombre de seances. Chaque seance est une reservation
 * ordinaire (planning, ticket, scan) rattachee a l'abonnement.
 */
@Entity
@Table(name = "abonnements")
public class Abonnement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "terrain_id")
    private Terrain terrain;

    @Column(name = "client_nom", nullable = false, length = 80)
    private String clientNom;

    @Column(name = "client_telephone", nullable = false, length = 20)
    private String clientTelephone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15)
    private Frequence frequence;

    @Column(name = "premiere_seance", nullable = false)
    private Instant premiereSeance;

    @Column(name = "nombre_seances", nullable = false)
    private int nombreSeances;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private ModeReglement reglement;

    /** Remise totale accordee (FCFA), repartie sur les seances. */
    @Column(nullable = false)
    private long remise;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private StatutAbonnement statut;

    @Column(name = "resilie_le")
    private Instant resilieLe;

    @Column(name = "cree_par")
    private String creePar;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Version
    private long version;

    protected Abonnement() {
    }

    public Abonnement(Terrain terrain, String clientNom, String clientTelephone, Frequence frequence,
                      Instant premiereSeance, int nombreSeances, ModeReglement reglement, long remise, String creePar) {
        this.terrain = terrain;
        this.clientNom = clientNom;
        this.clientTelephone = clientTelephone;
        this.frequence = frequence;
        this.premiereSeance = premiereSeance;
        this.nombreSeances = nombreSeances;
        this.reglement = reglement;
        this.remise = remise;
        this.creePar = creePar;
        this.statut = StatutAbonnement.ACTIF;
    }

    @PrePersist
    void avantCreation() {
        creeLe = Instant.now();
    }

    public void resilier(Instant quand) {
        if (statut != StatutAbonnement.ACTIF) {
            throw new ReservationInterditeException("DEJA_RESILIE", "Cet abonnement est déjà résilié.");
        }
        statut = StatutAbonnement.RESILIE;
        resilieLe = quand;
    }

    public Long getId() { return id; }
    public Terrain getTerrain() { return terrain; }
    public String getClientNom() { return clientNom; }
    public String getClientTelephone() { return clientTelephone; }
    public Frequence getFrequence() { return frequence; }
    public Instant getPremiereSeance() { return premiereSeance; }
    public int getNombreSeances() { return nombreSeances; }
    public ModeReglement getReglement() { return reglement; }
    public long getRemise() { return remise; }
    public StatutAbonnement getStatut() { return statut; }
    public Instant getResilieLe() { return resilieLe; }
    public Instant getCreeLe() { return creeLe; }
}
