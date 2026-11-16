package sn.terrainapp.backend.terrains.application;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import sn.terrainapp.backend.terrains.domaine.Blocage;
import sn.terrainapp.backend.terrains.domaine.HoraireOuverture;
import sn.terrainapp.backend.terrains.domaine.PlageTarifaire;
import sn.terrainapp.backend.terrains.domaine.Sport;
import sn.terrainapp.backend.terrains.domaine.Terrain;

/** Vues JSON du module terrains. Jours : 1 = lundi ... 7 = dimanche. */
public final class Vues {

    private Vues() {
    }

    public record SportVue(long id, String nom, int ordre, boolean actif, long version) {
        public static SportVue de(Sport s) {
            return new SportVue(s.getId(), s.getNom(), s.getOrdre(), s.isActif(), s.getVersion());
        }
    }

    public record PlageVue(Long id, String libelle, List<Integer> jours, LocalTime debut, LocalTime fin, long tarifHeure) {
        public static PlageVue de(PlageTarifaire p) {
            return new PlageVue(p.getId(), p.getLibelle(), p.listeJours().stream().map(DayOfWeek::getValue).sorted().toList(),
                    p.getDebut(), p.getFin(), p.getTarifHeure());
        }
    }

    /** photoUrl : couverture (premiere photo de la galerie). */
    public record TerrainVue(long id, long sportId, String sport, String nom, String description, String localite,
                             String surface, int capacite, List<String> equipements, String photoUrl,
                             List<String> photos, int dureeCreneauMinutes,
                             long tarifHeure, List<PlageVue> plages, boolean actif, int ordre, long version) {
        public static TerrainVue de(Terrain t) {
            return new TerrainVue(t.getId(), t.getSport().getId(), t.getSport().getNom(), t.getNom(), t.getDescription(),
                    t.getLocalite(), t.getSurface(), t.getCapacite(), t.listeEquipements(), t.getPhotoUrl(),
                    List.copyOf(t.getPhotos()), t.getDureeCreneauMinutes(),
                    t.getTarifHeure(), t.getPlages().stream().map(PlageVue::de).toList(), t.isActif(), t.getOrdre(),
                    t.getVersion());
        }
    }

    public record HoraireVue(int jour, boolean ouvert, LocalTime ouverture, LocalTime fermeture, boolean passeMinuit) {
        public static HoraireVue de(HoraireOuverture h) {
            return new HoraireVue(h.getJourSemaine().getValue(), h.isOuvert(), h.getOuverture(), h.getFermeture(),
                    h.passeMinuit());
        }
    }

    public record BlocageVue(long id, Long terrainId, Instant debut, Instant fin, String motif) {
        public static BlocageVue de(Blocage b) {
            return new BlocageVue(b.getId(), b.getTerrainId(), b.getDebut(), b.getFin(), b.getMotif());
        }
    }

    public record CreneauVue(Instant debut, Instant fin, long prix, Creneaux.Etat etat) {
        public static CreneauVue de(Creneaux.Creneau c) {
            return new CreneauVue(c.debut(), c.fin(), c.prix(), c.etat());
        }
    }

    /** Disponibilites d'un terrain pour un jour (App Client et planning). */
    public record DisponibilitesVue(long terrainId, String terrain, String sport, int dureeCreneauMinutes,
                                    List<CreneauVue> creneaux) {
    }
}
