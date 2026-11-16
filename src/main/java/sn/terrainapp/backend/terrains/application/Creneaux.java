package sn.terrainapp.backend.terrains.application;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import sn.terrainapp.backend.commun.temps.Horloge;
import sn.terrainapp.backend.terrains.domaine.Blocage;
import sn.terrainapp.backend.terrains.domaine.HoraireOuverture;
import sn.terrainapp.backend.terrains.domaine.PlageTarifaire;
import sn.terrainapp.backend.terrains.domaine.Terrain;
import sn.terrainapp.backend.terrains.infrastructure.HoraireOuvertureRepository;

/**
 * Calcul des creneaux d'un terrain pour un jour d'ouverture : de l'ouverture
 * a la fermeture du complexe, par pas de la duree de creneau du terrain.
 * Les creneaux apres minuit (fermeture 01:00) appartiennent au jour
 * d'ouverture. Prix : tarif horaire de la plage qui couvre le debut du
 * creneau, sinon celui du terrain, au prorata de la duree.
 */
@Component
public class Creneaux {

    public enum Etat { LIBRE, RESERVE, BLOQUE, PASSE }

    public record Creneau(Instant debut, Instant fin, long prix, Etat etat) {
    }

    /** Une periode occupee (reservation) sur un terrain. */
    public record Occupation(long terrainId, Instant debut, Instant fin) {
    }

    private final HoraireOuvertureRepository horaires;

    public Creneaux(HoraireOuvertureRepository horaires) {
        this.horaires = horaires;
    }

    public Map<DayOfWeek, HoraireOuverture> horairesParJour() {
        return horaires.findAll().stream().collect(Collectors.toMap(HoraireOuverture::getJourSemaine, Function.identity()));
    }

    /** Debut et fin d'ouverture du complexe pour ce jour, vide s'il est ferme. */
    public Optional<Instant[]> ouverture(LocalDate jour, Map<DayOfWeek, HoraireOuverture> parJour) {
        HoraireOuverture h = parJour.get(jour.getDayOfWeek());
        if (h == null || !h.isOuvert()) {
            return Optional.empty();
        }
        Instant debut = LocalDateTime.of(jour, h.getOuverture()).atZone(Horloge.DAKAR).toInstant();
        LocalDate jourFin = h.passeMinuit() ? jour.plusDays(1) : jour;
        Instant fin = LocalDateTime.of(jourFin, h.getFermeture()).atZone(Horloge.DAKAR).toInstant();
        return Optional.of(new Instant[] {debut, fin});
    }

    /** Creneaux du terrain pour ce jour, avec leur etat. */
    public List<Creneau> pour(Terrain terrain, LocalDate jour, Map<DayOfWeek, HoraireOuverture> parJour,
                              List<Occupation> occupations, List<Blocage> blocages, Instant maintenant) {
        List<Creneau> resultat = new ArrayList<>();
        Optional<Instant[]> plage = ouverture(jour, parJour);
        if (plage.isEmpty()) {
            return resultat;
        }
        long pas = terrain.getDureeCreneauMinutes() * 60L;
        for (Instant d = plage.get()[0]; !d.plusSeconds(pas).isAfter(plage.get()[1]); d = d.plusSeconds(pas)) {
            Instant f = d.plusSeconds(pas);
            final Instant debut = d;
            Etat etat;
            if (!debut.isAfter(maintenant)) {
                etat = Etat.PASSE;
            } else if (blocages.stream().anyMatch(b -> b.chevauche(terrain.getId(), debut, f))) {
                etat = Etat.BLOQUE;
            } else if (occupations.stream().anyMatch(o -> o.terrainId() == terrain.getId()
                    && o.debut().isBefore(f) && o.fin().isAfter(debut))) {
                etat = Etat.RESERVE;
            } else {
                etat = Etat.LIBRE;
            }
            resultat.add(new Creneau(debut, f, prix(terrain, debut), etat));
        }
        return resultat;
    }

    /**
     * Le creneau du terrain qui commence exactement a cet instant (sans
     * regarder s'il est libre), vide si l'heure ne correspond a aucun
     * creneau du jour (ou du jour precedent, pour la nuit).
     */
    public Optional<Creneau> commencantA(Terrain terrain, Instant debut) {
        Map<DayOfWeek, HoraireOuverture> parJour = horairesParJour();
        LocalDate jour = debut.atZone(Horloge.DAKAR).toLocalDate();
        for (LocalDate j : List.of(jour, jour.minusDays(1))) {
            Optional<Creneau> trouve = pour(terrain, j, parJour, List.of(), List.of(), Instant.MIN).stream()
                    .filter(c -> c.debut().equals(debut)).findFirst();
            if (trouve.isPresent()) {
                return trouve;
            }
        }
        return Optional.empty();
    }

    /** Prix d'un creneau commencant a cet instant. */
    public long prix(Terrain terrain, Instant debut) {
        ZonedDateTime z = debut.atZone(Horloge.DAKAR);
        long tarif = terrain.getPlages().stream()
                .filter(p -> p.couvre(z.getDayOfWeek(), z.toLocalTime()))
                .mapToLong(PlageTarifaire::getTarifHeure)
                .findFirst().orElse(terrain.getTarifHeure());
        return Math.round(tarif * terrain.getDureeCreneauMinutes() / 60.0);
    }
}
