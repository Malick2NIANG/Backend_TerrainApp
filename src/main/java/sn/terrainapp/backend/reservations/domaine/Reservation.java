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

    /** Abonnement dont cette reservation est une seance (null sinon). */
    @Column(name = "abonnement_id")
    private Long abonnementId;

    /** Seance d'abonnement payable le jour J : pas encaissee a la creation. */
    @jakarta.persistence.Transient
    private boolean aEncaisser;

    /** Especes : somme donnee par le client et monnaie rendue (recu de caisse). */
    @Column(name = "especes_recues")
    private Long especesRecues;

    @Column(name = "monnaie_rendue")
    private Long monnaieRendue;

    /** Date du paiement (recu) : confirmation en ligne, ou creation au comptoir. */
    @Column(name = "payee_le")
    private Instant payeeLe;

    @Column(name = "scanne_le")
    private Instant scanneLe;

    @Column(name = "scanne_par")
    private String scannePar;

    @Column(name = "annulee_le")
    private Instant annuleeLe;

    @Column(name = "annulee_par")
    private String annuleePar;

    /** Raison donnee par le personnel (client prevenu par SMS). */
    @Column(name = "motif_annulation", length = 200)
    private String motifAnnulation;

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
        if (statut == StatutReservation.CONFIRMEE && payeeLe == null && !aEncaisser) {
            payeeLe = creeLe;
        }
    }

    /** Seance d'abonnement : rattachee, et a encaisser le jour J si demande. */
    public void rattacherAbonnement(long abonnement, boolean encaisserLeJourJ) {
        this.abonnementId = abonnement;
        this.aEncaisser = encaisserLeJourJ;
    }

    /** Especes recues pour ce montant : la monnaie rendue est calculee. */
    public void noterEspeces(Long recues) {
        if (recues == null || moyenPaiement != MoyenPaiement.ESPECES) {
            return;
        }
        if (recues < montant) {
            throw new ReservationInterditeException("ESPECES_INSUFFISANTES", "La somme reçue est inférieure au montant à encaisser.");
        }
        this.especesRecues = recues;
        this.monnaieRendue = recues - montant;
    }

    /** Encaissement au comptoir d'une seance confirmee non payee. */
    public void encaisser(MoyenPaiement moyen, Instant quand) {
        if (payeeLe != null) {
            throw new ReservationInterditeException("DEJA_PAYEE", "Cette séance est déjà payée.");
        }
        if (statut != StatutReservation.CONFIRMEE && statut != StatutReservation.HONOREE) {
            throw new ReservationInterditeException("STATUT_INCOMPATIBLE", "Cette séance ne peut pas être encaissée.");
        }
        this.moyenPaiement = moyen;
        this.payeeLe = quand;
    }

    @PreUpdate
    void avantModification() {
        modifieLe = Instant.now();
    }

    public void confirmerPaiement() {
        if (statut != StatutReservation.EN_ATTENTE_PAIEMENT) {
            throw new ReservationInterditeException("STATUT_INCOMPATIBLE", "Cette réservation n'attend pas de paiement.");
        }
        statut = StatutReservation.CONFIRMEE;
        payeeLe = Instant.now();
    }

    /** Paiement refuse ou delai depasse : le creneau est libere. */
    public void expirer() {
        if (statut == StatutReservation.EN_ATTENTE_PAIEMENT) {
            statut = StatutReservation.EXPIREE;
        }
    }

    public void annuler(String par, Instant quand, String motif) {
        annuler(par, quand);
        this.motifAnnulation = motif;
    }

    public void annuler(String par, Instant quand) {
        if (statut != StatutReservation.CONFIRMEE && statut != StatutReservation.EN_ATTENTE_PAIEMENT) {
            throw new ReservationInterditeException("STATUT_INCOMPATIBLE", "Cette réservation ne peut plus être annulée.");
        }
        statut = StatutReservation.ANNULEE;
        annuleeLe = quand;
        annuleePar = par;
    }

    public void honorer(String par, Instant quand) {
        if (statut != StatutReservation.CONFIRMEE) {
            throw new ReservationInterditeException("STATUT_INCOMPATIBLE", "Ticket non valide pour l'entrée.");
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
    public Instant getPayeeLe() { return payeeLe; }
    public Long getAbonnementId() { return abonnementId; }
    public String getMotifAnnulation() { return motifAnnulation; }
    public Long getEspecesRecues() { return especesRecues; }
    public Long getMonnaieRendue() { return monnaieRendue; }
    public Instant getScanneLe() { return scanneLe; }
    public Instant getCreeLe() { return creeLe; }
    public long getVersion() { return version; }
}
