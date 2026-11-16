package sn.terrainapp.backend.terrains.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.terrainapp.backend.terrains.domaine.Sport;

public interface SportRepository extends JpaRepository<Sport, Long> {

    List<Sport> findAllByOrderByOrdreAscNomAsc();

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);

    boolean existsByNomIgnoreCase(String nom);
}
