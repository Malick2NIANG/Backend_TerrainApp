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

    Optional<Reservation> findByReference(String reference);

    boolean existsByReference(String reference);

    @Query("select r.id from Reservation r where r.statut = sn.terrainapp.backend.reservations.domaine.StatutReservation.EN_ATTENTE_PAIEMENT "
            + "and r.creeLe < :avant order by r.id")
    List<Long> enAttenteDePaiementAvant(@Param("avant") Instant avant, Pageable page);

    /** Historique sans compte : dernieres reservations d'un numero. */
    List<Reservation> findTop20ByClientTelephoneOrderByDebutDesc(String telephone);
}
