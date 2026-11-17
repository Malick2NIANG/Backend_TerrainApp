package sn.terrainapp.backend.annonces.application;

import static sn.terrainapp.backend.commun.audit.Libelles.guillemets;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.terrainapp.backend.annonces.domaine.Annonce;
import sn.terrainapp.backend.annonces.domaine.StatutAnnonce;
import sn.terrainapp.backend.annonces.domaine.TypeMedia;
import sn.terrainapp.backend.annonces.infrastructure.AnnonceRepository;
import sn.terrainapp.backend.commun.audit.JournalActions;
import sn.terrainapp.backend.commun.audit.JournalActions.Domaine;
import sn.terrainapp.backend.commun.diffusion.ChangementPublic;
import sn.terrainapp.backend.commun.erreurs.RequeteInvalideException;
import sn.terrainapp.backend.commun.erreurs.RessourceIntrouvableException;
import sn.terrainapp.backend.commun.securite.UtilisateurCourant;
import sn.terrainapp.backend.commun.validation.UrlImageValidator;
import sn.terrainapp.backend.commun.validation.UrlVideo;

/** Annonces du gerant (popup de l'App Client). */
@Service
@Transactional
public class AnnonceService {

    private final AnnonceRepository annonces;
    private final JournalActions journal;
    private final ApplicationEventPublisher evenements;

    public AnnonceService(AnnonceRepository annonces, JournalActions journal, ApplicationEventPublisher evenements) {
        this.annonces = annonces;
        this.journal = journal;
        this.evenements = evenements;
    }

    /**
     * Gerant : toutes les annonces, ou le statut demande. Tous les autres :
     * ACTIF seulement (demander un brouillon est refuse, 403).
     */
    @Transactional(readOnly = true)
    public List<AnnonceVue> lister(StatutAnnonce demande) {
        StatutAnnonce statut = demande;
        if (!UtilisateurCourant.estGerant()) {
            if (demande != null && demande != StatutAnnonce.ACTIF) {
                throw new AccessDeniedException("Seules les annonces publiées sont consultables.");
            }
            statut = StatutAnnonce.ACTIF;
        }
        return annonces.rechercher(statut).stream().map(AnnonceVue::de).toList();
    }

    public AnnonceVue creer(AnnonceSaisie s) {
        Annonce a = new Annonce(s.titre());
        a.remplacerSlides(slides(s));
        a = annonces.save(a);
        journal.enregistrer(Domaine.ANNONCE, "CREATION", String.valueOf(a.getId()),
                "a créé l'annonce " + guillemets(a.getTitre()));
        publier();
        return AnnonceVue.de(a);
    }

    public AnnonceVue modifier(long id, AnnonceSaisie s) {
        Annonce a = annonce(id);
        a.verifierVersion(s.version());
        a.renommer(s.titre());
        a.remplacerSlides(slides(s));
        journal.enregistrer(Domaine.ANNONCE, "MODIFICATION", String.valueOf(id),
                "a modifié l'annonce " + guillemets(a.getTitre()));
        publier();
        return AnnonceVue.de(a);
    }

    public AnnonceVue changerStatut(long id, StatutAnnonce cible, Long version) {
        Annonce a = annonce(id);
        a.verifierVersion(version);
        if (a.changerStatut(cible)) {
            String verbe = switch (cible) {
                case ACTIF -> "a publié";
                case ARCHIVE -> "a archivé";
                case BROUILLON -> "a retiré";
            };
            journal.enregistrer(Domaine.ANNONCE, "STATUT_" + cible, String.valueOf(id),
                    verbe + " l'annonce " + guillemets(a.getTitre()));
            publier();
        }
        return AnnonceVue.de(a);
    }

    private void publier() {
        evenements.publishEvent(new ChangementPublic(ChangementPublic.Sujet.ANNONCES));
    }

    private Annonce annonce(long id) {
        return annonces.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Annonce introuvable."));
    }

    /** Adresse du visuel coherente avec son type : une image n'est pas une video. */
    private static List<Annonce.SlideDonnees> slides(AnnonceSaisie s) {
        return s.slides().stream().map(sl -> {
            TypeMedia type = sl.mediaType() == null ? TypeMedia.IMAGE : sl.mediaType();
            boolean valide = type == TypeMedia.VIDEO ? UrlVideo.estValide(sl.mediaUrl())
                    : UrlImageValidator.estValide(sl.mediaUrl());
            if (!valide) {
                throw new RequeteInvalideException("MEDIA_INVALIDE", type == TypeMedia.VIDEO
                        ? "Vidéo invalide : déposez une vidéo MP4 ou WebM."
                        : "Image invalide : déposez une photo JPEG, PNG ou WebP.", "slides");
            }
            return new Annonce.SlideDonnees(sl.titre(), sl.message(), sl.mediaUrl(), type);
        }).toList();
    }
}
