package sn.terrainapp.backend.terrains.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import sn.terrainapp.backend.terrains.domaine.Terrain;

public interface TerrainRepository extends JpaRepository<Terrain, Long> {

    @Query("select t from Terrain t order by t.ordre, t.nom")
    List<Terrain> tous();

    @Query("select t from Terrain t where t.actif = true and t.sport.actif = true order by t.sport.ordre, t.ordre, t.nom")
    List<Terrain> visibles();

    /**
     * Verrou sur le terrain : deux reservations du meme terrain sont
     * traitees l'une apres l'autre, jamais de double reservation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Terrain t where t.id = :id")
    Optional<Terrain> verrouiller(@Param("id") long id);

    boolean existsBySportId(long sportId);
}
