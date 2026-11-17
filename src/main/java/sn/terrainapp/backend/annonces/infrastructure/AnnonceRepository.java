package sn.terrainapp.backend.annonces.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sn.terrainapp.backend.annonces.domaine.Annonce;
import sn.terrainapp.backend.annonces.domaine.StatutAnnonce;

public interface AnnonceRepository extends JpaRepository<Annonce, Long> {

    /** Statut null : toutes. La derniere publiee (ou modifiee) d'abord. */
    @Query("""
            select distinct a from Annonce a left join fetch a.slides
            where :statut is null or a.statut = :statut
            order by a.publieeLe desc, a.modifieLe desc""")
    List<Annonce> rechercher(@Param("statut") StatutAnnonce statut);
}
