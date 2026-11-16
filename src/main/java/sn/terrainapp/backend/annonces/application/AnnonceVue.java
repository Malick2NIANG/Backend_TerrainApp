package sn.terrainapp.backend.annonces.application;

import java.time.Instant;
import java.util.List;

import sn.terrainapp.backend.annonces.domaine.Annonce;
import sn.terrainapp.backend.annonces.domaine.AnnonceSlide;
import sn.terrainapp.backend.annonces.domaine.StatutAnnonce;
import sn.terrainapp.backend.annonces.domaine.TypeMedia;

/** Representation API d'une chaine d'annonces. */
public record AnnonceVue(
        String id,
        String titre,
        List<SlideVue> slides,
        StatutAnnonce statut,
        Instant publishedAt,
        long version,
        Instant updatedAt) {

    public record SlideVue(String id, String titre, String message, String mediaUrl, TypeMedia mediaType) {

        static SlideVue de(AnnonceSlide s) {
            return new SlideVue(String.valueOf(s.getId()), s.getTitre(), s.getMessage(), s.getMediaUrl(), s.getMediaType());
        }
    }

    public static AnnonceVue de(Annonce a) {
        return new AnnonceVue(String.valueOf(a.getId()), a.getTitre(),
                a.getSlides().stream().map(SlideVue::de).toList(),
                a.getStatut(), a.getPublieeLe(), a.getVersion(), a.getModifieLe());
    }
}
