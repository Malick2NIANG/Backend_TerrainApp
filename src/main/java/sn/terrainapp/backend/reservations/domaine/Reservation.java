package sn.terrainapp.backend.reservations.domaine;

import java.time.Instant;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import sn.terrainapp.backend.paiements.domaine.MoyenPaiement;
import sn.terrainapp.backend.terrains.domaine.Terrain;

/** Reservation d'un creneau de terrain par un joueur (sans compte : nom + telephone). */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 12)
    private String reference;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "terrain_id")
    private Terrain terrain;

    @Column(nullable = false)
    private Instant debut;

    @Column(nullable = false)
    private Instant fin;

    @Column(nullable = false)
    private long montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutReservation statut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Canal canal;

    @Column(name = "moyen_paiement", nullable = false, length = 20)
    private MoyenPaiement moyenPaiement;

    @Column(name = "client_nom", nullable = false, length = 80)
    private String clientNom;

    @Column(name = "client_telephone", nullable = false, length = 20)
    private String clientTelephone;

    @Column(name = "jeton_suivi_empreinte", length = 64)
    private String jetonSuiviEmpreinte;

    @Column(name = "code_ticket", nullable = false, length = 40)
    private String codeTicket;

    @Column(name = "scanne_le")
    private Instant scanneLe;

    @Column(name = "scanne_par")
    private String scannePar;

    @Column(name = "annulee_le")
    private Instant annuleeLe;

    @Column(name = "annulee_par")
    private String annuleePar;

    @Column(name = "cree_par")
    private String creePar;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    @Version
    private long version;

    protected Reservation() {
    }

    public Reservation(String reference, Terrain terrain, Instant debut, Instant fin, long montant, Canal canal,
                       MoyenPaiement moyenPaiement, String clientNom, String clientTelephone,
                       String jetonSuiviEmpreinte, String codeTicket, String creePar) {
        this.reference = reference;
        this.terrain = terrain;
        this.debut = debut;
        this.fin = fin;
        this.montant = montant;
        this.canal = canal;
        this.moyenPaiement = moyenPaiement;
        this.clientNom = clientNom;
        this.clientTelephone = clientTelephone;
        this.jetonSuiviEmpreinte = jetonSuiviEmpreinte;
        this.codeTicket = codeTicket;
        this.creePar = creePar;
        this.statut = canal == Canal.COMPTOIR ? StatutReservation.CONFIRMEE : StatutReservation.EN_ATTENTE_PAIEMENT;
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

    public void confirmerPaiement() {
        if (statut != StatutReservation.EN_ATTENTE_PAIEMENT) {
            throw new ReservationInterditeException("STATUT_INCOMPATIBLE", "Cette reservation n'attend pas de paiement.");
        }
        statut = StatutReservation.CONFIRMEE;
    }

    /** Paiement refuse ou delai depasse : le creneau est libere. */
    public void expirer() {
        if (statut == StatutReservation.EN_ATTENTE_PAIEMENT) {
            statut = StatutReservation.EXPIREE;
        }
    }

    public void annuler(String par, Instant quand) {
        if (statut != StatutReservation.CONFIRMEE && statut != StatutReservation.EN_ATTENTE_PAIEMENT) {
            throw new ReservationInterditeException("STATUT_INCOMPATIBLE", "Cette reservation ne peut plus etre annulee.");
        }
        statut = StatutReservation.ANNULEE;
        annuleeLe = quand;
        annuleePar = par;
    }

    public void honorer(String par, Instant quand) {
        if (statut != StatutReservation.CONFIRMEE) {
            throw new ReservationInterditeException("STATUT_INCOMPATIBLE", "Ticket non valide pour l'entree.");
        }
        statut = StatutReservation.HONOREE;
        scanneLe = quand;
        scannePar = par;
    }

    /** Paiement arrive apres expiration ou annulation : a rembourser. */
    public boolean paiementTardif() {
        return statut == StatutReservation.EXPIREE || statut == StatutReservation.ANNULEE;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public Terrain getTerrain() { return terrain; }
    public Instant getDebut() { return debut; }
    public Instant getFin() { return fin; }
    public long getMontant() { return montant; }
    public StatutReservation getStatut() { return statut; }
    public Canal getCanal() { return canal; }
    public MoyenPaiement getMoyenPaiement() { return moyenPaiement; }
    public String getClientNom() { return clientNom; }
    public String getClientTelephone() { return clientTelephone; }
    public String getJetonSuiviEmpreinte() { return jetonSuiviEmpreinte; }
    public String getCodeTicket() { return codeTicket; }
    public Instant getScanneLe() { return scanneLe; }
    public Instant getCreeLe() { return creeLe; }
    public long getVersion() { return version; }
}
