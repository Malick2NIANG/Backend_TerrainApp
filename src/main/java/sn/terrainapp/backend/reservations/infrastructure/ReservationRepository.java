package sn.terrainapp.backend.reservations.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import sn.terrainapp.backend.reservations.domaine.Reservation;
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

    /** Historique sans compte : dernieres reservations d'un numero. */
    /** Reservations d'un numero protegees par un code PIN (App), les plus recentes d'abord. */
    List<Reservation> findTop60ByClientTelephoneAndPinEmpreinteIsNotNullOrderByDebutDesc(String telephone);

    /**
     * Reservations d'un numero concernees par le code PIN : en ligne, ou au
     * comptoir quand le client a choisi un PIN (recuperation par SMS).
     */
    @Query("select r from Reservation r where r.clientTelephone = :telephone and "
            + "(r.canal = sn.terrainapp.backend.reservations.domaine.Canal.APP or r.pinEmpreinte is not null)")
    List<Reservation> avecCodePin(@Param("telephone") String telephone);
}
