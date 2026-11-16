package sn.terrainapp.backend.terrains.infrastructure;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sn.terrainapp.backend.terrains.domaine.Blocage;

public interface BlocageRepository extends JpaRepository<Blocage, Long> {

    @Query("select b from Blocage b where b.debut < :fin and b.fin > :debut order by b.debut")
    List<Blocage> entre(@Param("debut") Instant debut, @Param("fin") Instant fin);
}
