package sn.terrainapp.backend.avis.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import sn.terrainapp.backend.avis.domaine.Avis;

public interface AvisRepository extends JpaRepository<Avis, Long> {

    Optional<Avis> findByReservationId(long reservationId);

    List<Avis> findByReservationIdIn(List<Long> reservationIds);

    List<Avis> findByTerrainIdAndVisibleTrueOrderByCreeLeDesc(long terrainId, Pageable page);

    List<Avis> findAllByOrderByCreeLeDesc(Pageable page);

    /** [terrainId, moyenne, nombre] des avis visibles. */
    @Query("select a.terrainId, avg(a.note), count(a) from Avis a where a.visible = true group by a.terrainId")
    List<Object[]> statsParTerrain();
}
