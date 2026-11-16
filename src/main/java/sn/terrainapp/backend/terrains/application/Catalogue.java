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

    public Catalogue(SportRepository sports, TerrainRepository terrains, HoraireOuvertureRepository horaires,
                     BlocageRepository blocages, ReservationRepository reservations, Creneaux creneaux, Clock horloge) {
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
        return (tous ? terrains.tous() : terrains.visibles()).stream().map(TerrainVue::de).toList();
    }

    public TerrainVue terrain(long id, boolean tous) {
        Terrain t = terrains.findById(id).filter(x -> tous || (x.isActif() && x.getSport().isActif()))
                .orElseThrow(() -> new RessourceIntrouvableException("Terrain introuvable."));
        return TerrainVue.de(t);
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
