package sn.terrainapp.backend.terrains.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.terrainapp.backend.commun.erreurs.RequeteInvalideException;
import sn.terrainapp.backend.commun.erreurs.RessourceIntrouvableException;
import sn.terrainapp.backend.commun.temps.Horloge;
import sn.terrainapp.backend.reservations.domaine.StatutReservation;
import sn.terrainapp.backend.reservations.infrastructure.ReservationRepository;
import sn.terrainapp.backend.terrains.application.Creneaux.Occupation;
import sn.terrainapp.backend.terrains.application.Vues.CreneauVue;
import sn.terrainapp.backend.terrains.application.Vues.DisponibilitesVue;
import sn.terrainapp.backend.terrains.application.Vues.HoraireVue;
import sn.terrainapp.backend.terrains.application.Vues.SportVue;
import sn.terrainapp.backend.terrains.application.Vues.TerrainVue;
import sn.terrainapp.backend.terrains.domaine.Blocage;
import sn.terrainapp.backend.terrains.domaine.HoraireOuverture;
import sn.terrainapp.backend.terrains.domaine.Terrain;
import sn.terrainapp.backend.terrains.infrastructure.BlocageRepository;
import sn.terrainapp.backend.terrains.infrastructure.HoraireOuvertureRepository;
import sn.terrainapp.backend.terrains.infrastructure.SportRepository;
import sn.terrainapp.backend.terrains.infrastructure.TerrainRepository;

/** Lectures : sports, terrains, horaires et disponibilites d'un jour. */
@Service
@Transactional(readOnly = true)
public class Catalogue {

    /** On ne reserve pas au-dela de ce nombre de jours. */
    public static final int JOURS_RESERVABLES = 60;

    static final EnumSet<StatutReservation> OCCUPANTS = EnumSet.of(StatutReservation.EN_ATTENTE_PAIEMENT,
            StatutReservation.CONFIRMEE, StatutReservation.HONOREE);

    private final SportRepository sports;
    private final TerrainRepository terrains;
    private final HoraireOuvertureRepository horaires;
    private final BlocageRepository blocages;
    private final ReservationRepository reservations;
    private final Creneaux creneaux;
    private final Clock horloge;
    private final sn.terrainapp.backend.avis.application.AvisService avis;

    public Catalogue(SportRepository sports, TerrainRepository terrains, HoraireOuvertureRepository horaires,
                     BlocageRepository blocages, ReservationRepository reservations, Creneaux creneaux, Clock horloge,
                     sn.terrainapp.backend.avis.application.AvisService avis) {
        this.avis = avis;
        this.sports = sports;
        this.terrains = terrains;
        this.horaires = horaires;
        this.blocages = blocages;
        this.reservations = reservations;
        this.creneaux = creneaux;
        this.horloge = horloge;
    }

    /** Sports : actifs seulement pour le public, tous pour le gerant. */
    public List<SportVue> sports(boolean tous) {
        return sports.findAllByOrderByOrdreAscNomAsc().stream().filter(s -> tous || s.isActif()).map(SportVue::de).toList();
    }

    public List<TerrainVue> terrains(boolean tous) {
        var stats = avis.stats();
        return (tous ? terrains.tous() : terrains.visibles()).stream().map(t -> vue(t, stats)).toList();
    }

    private static TerrainVue vue(Terrain t, Map<Long, sn.terrainapp.backend.avis.application.AvisService.Stats> stats) {
        var s = stats.get(t.getId());
        return s == null ? TerrainVue.de(t) : TerrainVue.de(t, s.moyenne(), s.nombre());
    }

    /**
     * Terrains les plus reserves des 30 derniers jours (accueil de l'App
     * Client), puis dans l'ordre d'affichage du gerant. Les nombres ne sont
     * pas exposes : seulement le classement.
     */
    public List<TerrainVue> tendances(int nombre) {
        Instant depuis = Instant.now(horloge).minus(java.time.Duration.ofDays(30));
        Map<Long, Long> comptes = new java.util.HashMap<>();
        for (Object[] ligne : reservations.comptesParTerrain(depuis,
                EnumSet.of(StatutReservation.CONFIRMEE, StatutReservation.HONOREE))) {
            comptes.put((Long) ligne[0], (Long) ligne[1]);
        }
        List<Terrain> visibles = terrains.visibles();
        var stats = avis.stats();
        return visibles.stream()
                .sorted(java.util.Comparator.comparingLong((Terrain t) -> -comptes.getOrDefault(t.getId(), 0L))
                        .thenComparingInt(visibles::indexOf))
                .limit(Math.max(1, Math.min(nombre, 8)))
                .map(t -> vue(t, stats)).toList();
    }

    public TerrainVue terrain(long id, boolean tous) {
        Terrain t = terrains.findById(id).filter(x -> tous || (x.isActif() && x.getSport().isActif()))
                .orElseThrow(() -> new RessourceIntrouvableException("Terrain introuvable."));
        return vue(t, avis.stats());
    }

    public List<HoraireVue> horaires() {
        return horaires.findAllByOrderByJourAsc().stream().map(HoraireVue::de).toList();
    }

    /**
     * Disponibilites de tous les terrains visibles (ou d'un seul) pour un
     * jour d'ouverture. Personnel : aussi les terrains inactifs.
     */
    public List<DisponibilitesVue> disponibilites(LocalDate jour, Long terrainId, boolean personnel) {
        LocalDate aujourdhui = LocalDate.now(horloge);
        if (!personnel && (jour.isBefore(aujourdhui) || jour.isAfter(aujourdhui.plusDays(JOURS_RESERVABLES)))) {
            throw new RequeteInvalideException("DATE_HORS_PERIODE",
                    "Choisissez une date entre aujourd'hui et dans " + JOURS_RESERVABLES + " jours.", "date");
        }
        List<Terrain> liste = (personnel ? terrains.tous() : terrains.visibles()).stream()
                .filter(t -> terrainId == null || t.getId().equals(terrainId)).toList();
        if (terrainId != null && liste.isEmpty()) {
            throw new RessourceIntrouvableException("Terrain introuvable.");
        }
        Map<DayOfWeek, HoraireOuverture> parJour = creneaux.horairesParJour();
        Instant debut = LocalDateTime.of(jour, LocalTime.MIN).atZone(Horloge.DAKAR).toInstant();
        Instant fin = debut.plusSeconds(2 * 86_400L);
        List<Occupation> occupations = reservations.entre(debut, fin, OCCUPANTS).stream()
                .map(r -> new Occupation(r.getTerrain().getId(), r.getDebut(), r.getFin())).toList();
        List<Blocage> fermetures = blocages.entre(debut, fin);
        Instant maintenant = Instant.now(horloge);
        return liste.stream().map(t -> new DisponibilitesVue(t.getId(), t.getNom(), t.getSport().getNom(),
                t.getDureeCreneauMinutes(),
                creneaux.pour(t, jour, parJour, occupations, fermetures, maintenant).stream().map(CreneauVue::de).toList()))
                .toList();
    }
}
