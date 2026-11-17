package sn.terrainapp.backend.abonnements.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import sn.terrainapp.backend.abonnements.domaine.Abonnement;

public interface AbonnementRepository extends JpaRepository<Abonnement, Long> {

    List<Abonnement> findAllByOrderByStatutAscCreeLeDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Abonnement a where a.id = :id")
    Optional<Abonnement> verrouiller(@Param("id") long id);
}
