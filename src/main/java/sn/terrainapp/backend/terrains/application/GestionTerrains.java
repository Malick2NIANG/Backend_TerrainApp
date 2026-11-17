package sn.terrainapp.backend.terrains.application;

import static sn.terrainapp.backend.commun.audit.Libelles.fcfa;
import static sn.terrainapp.backend.commun.audit.Libelles.guillemets;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.terrainapp.backend.commun.audit.JournalActions;
import sn.terrainapp.backend.commun.audit.JournalActions.Domaine;
import sn.terrainapp.backend.commun.diffusion.ChangementPublic;
import sn.terrainapp.backend.commun.erreurs.ConflitException;
import sn.terrainapp.backend.commun.erreurs.RequeteInvalideException;
import sn.terrainapp.backend.commun.erreurs.RessourceIntrouvableException;
import sn.terrainapp.backend.commun.securite.UtilisateurCourant;
import sn.terrainapp.backend.commun.validation.UrlImageValidator;
import sn.terrainapp.backend.commun.validation.UrlVideo;
import sn.terrainapp.backend.terrains.application.Vues.BlocageVue;
import sn.terrainapp.backend.terrains.application.Vues.HoraireVue;
import sn.terrainapp.backend.terrains.application.Vues.SportVue;
import sn.terrainapp.backend.terrains.application.Vues.TerrainVue;
import sn.terrainapp.backend.terrains.domaine.Blocage;
import sn.terrainapp.backend.terrains.domaine.HoraireOuverture;
import sn.terrainapp.backend.terrains.domaine.HoraireTerrain;
import sn.terrainapp.backend.terrains.domaine.PlageTarifaire;
import sn.terrainapp.backend.terrains.domaine.Sport;
import sn.terrainapp.backend.terrains.domaine.Terrain;
import sn.terrainapp.backend.terrains.infrastructure.BlocageRepository;
import sn.terrainapp.backend.terrains.infrastructure.HoraireOuvertureRepository;
import sn.terrainapp.backend.terrains.infrastructure.SportRepository;
import sn.terrainapp.backend.terrains.infrastructure.TerrainRepository;

/** Ecritures du gerant : sports, terrains et tarifs, horaires, blocages. */
@Service
@Transactional
public class GestionTerrains {

    public static final int MAX_PHOTOS = 10;

    public record SaisieSport(@NotBlank @Size(max = 60) String nom, @Min(0) int ordre, Boolean actif) {
    }

    /** Jours : 1 = lundi ... 7 = dimanche. */
    public record SaisiePlage(@NotBlank @Size(max = 60) String libelle,
                              @NotEmpty Set<@Min(1) @Max(7) Integer> jours,
                              @NotNull LocalTime debut, @NotNull LocalTime fin, @Min(0) long tarifHeure) {
    }

    /** photos : galerie de l'annonce (photos et videos), le premier media est la couverture. */
    public record SaisieTerrain(@NotNull Long sportId, @NotBlank @Size(max = 80) String nom,
                                @Size(max = 1000) String description, @Size(max = 120) String localite,
                                @DecimalMin("-90") @DecimalMax("90") Double latitude,
                                @DecimalMin("-180") @DecimalMax("180") Double longitude,
                                @Size(max = 60) String surface,
                                @Min(1) @Max(100) int capacite, List<@Size(max = 40) String> equipements,
                                @Size(max = MAX_PHOTOS, message = "10 photos ou vidéos au maximum.") List<@NotBlank @Size(max = 500) String> photos,
                                @Min(30) @Max(240) int dureeCreneauMinutes,
                                @Min(0) long tarifHeure, List<@Valid SaisiePlage> plages, Boolean actif,
                                @Min(0) int ordre, List<@Valid SaisieHoraire> horaires) {
    }

    public record SaisieHoraire(@Min(1) @Max(7) int jour, boolean ouvert, @NotNull LocalTime ouverture,
                                @NotNull LocalTime fermeture) {
    }

    public record SaisieBlocage(Long terrainId, @NotNull Instant debut, @NotNull Instant fin,
                                @Size(max = 200) String motif) {
    }

    private final SportRepository sports;
    private final TerrainRepository terrains;
    private final HoraireOuvertureRepository horaires;
    private final BlocageRepository blocages;
    private final JournalActions journal;
    private final org.springframework.context.ApplicationEventPublisher evenements;
    private final Clock horloge;

    public GestionTerrains(SportRepository sports, TerrainRepository terrains, HoraireOuvertureRepository horaires,
                           BlocageRepository blocages, JournalActions journal,
                           org.springframework.context.ApplicationEventPublisher evenements, Clock horloge) {
        this.sports = sports;
        this.terrains = terrains;
        this.horaires = horaires;
        this.blocages = blocages;
        this.journal = journal;
        this.evenements = evenements;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------ sports

    public SportVue creerSport(SaisieSport s) {
        if (sports.existsByNomIgnoreCase(s.nom().trim())) {
            throw new ConflitException("SPORT_EXISTANT", "Ce sport existe déjà.");
        }
        Sport sport = sports.save(new Sport(s.nom(), s.ordre()));
        journal.enregistrer(Domaine.SPORT, "CREATION", String.valueOf(sport.getId()), "a créé le sport " + guillemets(sport.getNom()));
        publier();
        return SportVue.de(sport);
    }

    public SportVue modifierSport(long id, SaisieSport s) {
        Sport sport = sports.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Sport introuvable."));
        if (sports.existsByNomIgnoreCaseAndIdNot(s.nom().trim(), id)) {
            throw new ConflitException("SPORT_EXISTANT", "Ce sport existe déjà.");
        }
        sport.modifier(s.nom(), s.ordre(), s.actif() == null || s.actif());
        journal.enregistrer(Domaine.SPORT, "MODIFICATION", String.valueOf(id), "a modifié le sport " + guillemets(sport.getNom()));
        publier();
        return SportVue.de(sport);
    }

    public void supprimerSport(long id) {
        Sport sport = sports.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Sport introuvable."));
        if (terrains.existsBySportId(id)) {
            throw new ConflitException("SPORT_UTILISE", "Des terrains utilisent ce sport : masquez-le plutôt.");
        }
        sports.delete(sport);
        journal.enregistrer(Domaine.SPORT, "SUPPRESSION", String.valueOf(id), "a supprimé le sport " + guillemets(sport.getNom()));
        publier();
    }

    // ------------------------------------------------------------ terrains

    public TerrainVue creerTerrain(SaisieTerrain s) {
        Terrain t = new Terrain(sport(s.sportId()));
        appliquer(t, s);
        t = terrains.save(t);
        journal.enregistrer(Domaine.TERRAIN, "CREATION", String.valueOf(t.getId()), "a créé le terrain "
                + guillemets(t.getNom()) + " (" + fcfa(t.getTarifHeure()) + " / h)");
        publier();
        return TerrainVue.de(t);
    }

    public TerrainVue modifierTerrain(long id, SaisieTerrain s) {
        Terrain t = terrains.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Terrain introuvable."));
        appliquer(t, s);
        journal.enregistrer(Domaine.TERRAIN, "MODIFICATION", String.valueOf(id), "a modifié le terrain " + guillemets(t.getNom()));
        publier();
        return TerrainVue.de(t);
    }

    private void appliquer(Terrain t, SaisieTerrain s) {
        for (String media : s.photos() == null ? List.<String>of() : s.photos()) {
            if (!UrlImageValidator.estValide(media) && !UrlVideo.estValide(media)) {
                throw new RequeteInvalideException("MEDIA_INVALIDE",
                        "Galerie : photo (JPEG, PNG, WebP) ou vidéo (MP4, WebM) attendue.", "photos");
            }
        }
        t.modifier(sport(s.sportId()), s.nom(), s.description(), s.localite(), s.surface(), s.capacite(),
                s.equipements(), s.photos(), s.dureeCreneauMinutes(), s.tarifHeure(), s.actif() == null || s.actif(), s.ordre());
        if ((s.latitude() == null) != (s.longitude() == null)) {
            throw new RequeteInvalideException("GPS_INCOMPLET", "Position GPS : indiquez la latitude ET la longitude.", "latitude");
        }
        t.placer(s.latitude(), s.longitude());
        List<SaisiePlage> plages = s.plages() == null ? List.of() : s.plages();
        for (SaisiePlage p : plages) {
            if (p.debut().equals(p.fin())) {
                throw new RequeteInvalideException("PLAGE_INVALIDE", "Plage " + guillemets(p.libelle())
                        + " : le début et la fin doivent être différents.", "plages");
            }
        }
        t.remplacerPlages(plages.stream().map(p -> new PlageTarifaire(p.libelle(), jours(p.jours()), p.debut(), p.fin(),
                p.tarifHeure())).toList());
        t.remplacerHoraires(horairesPropres(s.horaires()));
    }

    /** Horaires propres : vides (= ceux du complexe) ou les 7 jours, chacun une fois. */
    private static List<HoraireTerrain> horairesPropres(List<SaisieHoraire> saisies) {
        if (saisies == null || saisies.isEmpty()) {
            return List.of();
        }
        if (saisies.size() != 7 || saisies.stream().map(SaisieHoraire::jour).distinct().count() != 7) {
            throw new RequeteInvalideException("HORAIRES_INCOMPLETS", "Indiquez les horaires des 7 jours.", "horaires");
        }
        for (SaisieHoraire h : saisies) {
            if (h.ouvert() && h.ouverture().equals(h.fermeture())) {
                throw new RequeteInvalideException("HORAIRE_INVALIDE", "L'ouverture et la fermeture doivent être différentes.", "horaires");
            }
        }
        return saisies.stream().map(h -> new HoraireTerrain(h.jour(), h.ouvert(), h.ouverture(), h.fermeture())).toList();
    }

    private static Set<DayOfWeek> jours(Set<Integer> numeros) {
        return numeros.stream().map(DayOfWeek::of).collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    private Sport sport(long id) {
        return sports.findById(id).orElseThrow(() -> new RequeteInvalideException("SPORT_INCONNU", "Sport introuvable.", "sportId"));
    }

    // ------------------------------------------------------------ horaires

    public List<HoraireVue> modifierHoraires(List<SaisieHoraire> saisies) {
        for (SaisieHoraire s : saisies) {
            HoraireOuverture h = horaires.findById(s.jour()).orElseThrow();
            if (s.ouvert() && s.ouverture().equals(s.fermeture())) {
                throw new RequeteInvalideException("HORAIRE_INVALIDE", "L'ouverture et la fermeture doivent être différentes.", "horaires");
            }
            h.modifier(s.ouvert(), s.ouverture(), s.fermeture());
        }
        journal.enregistrer(Domaine.REGLAGE, "HORAIRES", null, "a modifié les horaires d'ouverture");
        publier();
        return horaires.findAllByOrderByJourAsc().stream().map(HoraireVue::de).toList();
    }

    // ------------------------------------------------------------ blocages

    @Transactional(readOnly = true)
    public List<BlocageVue> blocages(Instant debut, Instant fin) {
        return blocages.entre(debut, fin).stream().map(BlocageVue::de).toList();
    }

    public BlocageVue bloquer(SaisieBlocage s) {
        if (!s.fin().isAfter(s.debut())) {
            throw new RequeteInvalideException("PERIODE_INVALIDE", "La fin doit être après le début.", "fin");
        }
        String nomTerrain = "tout le complexe";
        if (s.terrainId() != null) {
            nomTerrain = terrains.findById(s.terrainId()).map(Terrain::getNom)
                    .orElseThrow(() -> new RequeteInvalideException("TERRAIN_INCONNU", "Terrain introuvable.", "terrainId"));
        }
        Blocage b = blocages.save(new Blocage(s.terrainId(), s.debut(), s.fin(), s.motif(),
                UtilisateurCourant.identifiant().orElse("systeme"), Instant.now(horloge)));
        journal.enregistrer(Domaine.BLOCAGE, "CREATION", String.valueOf(b.getId()), "a bloqué " + nomTerrain);
        publier();
        return BlocageVue.de(b);
    }

    public void debloquer(long id) {
        Blocage b = blocages.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Blocage introuvable."));
        blocages.delete(b);
        journal.enregistrer(Domaine.BLOCAGE, "SUPPRESSION", String.valueOf(id), "a levé un blocage");
        publier();
    }

    private void publier() {
        evenements.publishEvent(new ChangementPublic(ChangementPublic.Sujet.TERRAINS));
    }
}
