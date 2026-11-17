package sn.terrainapp.backend.reservations.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import sn.terrainapp.backend.reservations.domaine.CodeRecuperation;

public interface CodeRecuperationRepository extends JpaRepository<CodeRecuperation, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CodeRecuperation c where c.telephone = :telephone")
    Optional<CodeRecuperation> verrouiller(@Param("telephone") String telephone);
}
