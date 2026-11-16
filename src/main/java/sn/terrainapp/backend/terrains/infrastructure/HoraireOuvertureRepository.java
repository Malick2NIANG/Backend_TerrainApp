package sn.terrainapp.backend.terrains.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.terrainapp.backend.terrains.domaine.HoraireOuverture;

public interface HoraireOuvertureRepository extends JpaRepository<HoraireOuverture, Integer> {

    List<HoraireOuverture> findAllByOrderByJourAsc();
}
