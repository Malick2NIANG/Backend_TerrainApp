package sn.terrainapp.backend.reservations.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import sn.terrainapp.backend.reservations.domaine.Reservation;
import sn.terrainapp.backend.paiements.domaine.MoyenPaiement;
import sn.terrainapp.backend.reservations.domaine.StatutReservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Reservations qui occupent un creneau sur la periode (tous terrains). */
    @Query("select r from Reservation r where r.statut in :statuts and r.debut < :fin and r.fin > :debut order by r.debut")
    List<Reservation> entre(@Param("debut") Instant debut, @Param("fin") Instant fin,
                            @Param("statuts") Collection<StatutReservation> statuts);

    @Query("select count(r) > 0 from Reservation r where r.terrain.id = :terrain and r.statut in :statuts "
            + "and r.debut < :fin and r.fin > :debut")
    boolean chevauchement(@Param("terrain") long terrain, @Param("debut") Instant debut, @Param("fin") Instant fin,
                          @Param("statuts") Collection<StatutReservation> statuts);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> verrouiller(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.codeTicket = :code")
    Optional<Reservation> verrouillerParTicket(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.reference = :reference")
    Optional<Reservation> verrouillerParReference(@Param("reference") String reference);

    Optional<Reservation> findByReference(String reference);

    /** [terrainId, nombre] des reservations depuis une date (terrains les plus reserves). */
    @Query("select r.terrain.id, count(r) from Reservation r where r.statut in :statuts and r.debut >= :depuis "
            + "group by r.terrain.id")
    List<Object[]> comptesParTerrain(@Param("depuis") Instant depuis, @Param("statuts") Collection<StatutReservation> statuts);

    boolean existsByReference(String reference);

    @Query("select r.id from Reservation r where r.statut = sn.terrainapp.backend.reservations.domaine.StatutReservation.EN_ATTENTE_PAIEMENT "
            + "and r.creeLe < :avant order by r.id")
    List<Long> enAttenteDePaiementAvant(@Param("avant") Instant avant, Pageable page);

    /** Reservations d'un numero (en ligne et au comptoir), les plus recentes d'abord. */
    List<Reservation> findTop60ByClientTelephoneOrderByDebutDesc(String telephone);

    boolean existsByClientTelephone(String telephone);

    /** Seances d'un ou plusieurs abonnements, dans l'ordre. */
    List<Reservation> findByAbonnementIdInOrderByDebut(Collection<Long> abonnements);

    /** Reservations a venir sur un terrain (archivage impossible tant qu'il y en a). */
    long countByTerrainIdAndStatutInAndDebutAfter(Long terrainId, Collection<StatutReservation> statuts, Instant apres);

    /** Filtres de l'historique du gerant (periode sur la date du match). */
    String FILTRES_HISTORIQUE = " where r.debut >= :du and r.debut < :au"
            + " and (:terrain is null or r.terrain.id = :terrain)"
            + " and (:moyen is null or r.moyenPaiement = :moyen)"
            + " and (:recherche is null or lower(r.clientNom) like :recherche or r.clientTelephone like :recherche"
            + " or lower(r.reference) like :recherche)";

    @Query(value = "select r from Reservation r join fetch r.terrain t join fetch t.sport" + FILTRES_HISTORIQUE
            + " and (:statut is null or r.statut = :statut) order by r.debut desc, r.id desc",
            countQuery = "select count(r) from Reservation r" + FILTRES_HISTORIQUE + " and (:statut is null or r.statut = :statut)")
    Page<Reservation> historique(@Param("du") Instant du, @Param("au") Instant au, @Param("terrain") Long terrain,
                                 @Param("moyen") MoyenPaiement moyen, @Param("recherche") String recherche,
                                 @Param("statut") StatutReservation statut, Pageable page);

    /** [statut, nombre] sur la periode et les filtres, tous statuts (cartes de l'historique). */
    @Query("select r.statut, count(r) from Reservation r" + FILTRES_HISTORIQUE + " group by r.statut")
    List<Object[]> historiqueParStatut(@Param("du") Instant du, @Param("au") Instant au, @Param("terrain") Long terrain,
                                       @Param("moyen") MoyenPaiement moyen, @Param("recherche") String recherche);

    /** [moyen, nombre, somme] des reservations payees et non annulees (recette de la periode). */
    @Query("select r.moyenPaiement, count(r), sum(r.montant) from Reservation r" + FILTRES_HISTORIQUE
            + " and r.payeeLe is not null and r.statut in (sn.terrainapp.backend.reservations.domaine.StatutReservation.CONFIRMEE,"
            + " sn.terrainapp.backend.reservations.domaine.StatutReservation.HONOREE)"
            + " and (:statut is null or r.statut = :statut) group by r.moyenPaiement")
    List<Object[]> historiqueEncaisse(@Param("du") Instant du, @Param("au") Instant au, @Param("terrain") Long terrain,
                                      @Param("moyen") MoyenPaiement moyen, @Param("recherche") String recherche,
                                      @Param("statut") StatutReservation statut);
}
