package sn.terrainapp.backend.reservations.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.terrainapp.backend.commun.erreurs.RequeteInvalideException;
import sn.terrainapp.backend.commun.temps.Horloge;
import sn.terrainapp.backend.paiements.domaine.MoyenPaiement;
import sn.terrainapp.backend.reservations.domaine.Reservation;
import sn.terrainapp.backend.reservations.domaine.StatutReservation;
import sn.terrainapp.backend.reservations.infrastructure.ReservationRepository;

/**
 * Historique des reservations pour le gerant : une periode (date du match,
 * un an au plus), des filtres, une page de resultats, la repartition par
 * statut (cartes) et la recette encaissee de la periode, par moyen.
 */
@Service
public class HistoriqueReservations {

    public static final int JOURS_MAX = 366;
    public static final int TAILLE_MAX = 5000;

    public record Filtres(LocalDate du, LocalDate au, Long terrainId, StatutReservation statut,
                          MoyenPaiement moyen, String recherche) {
    }

    /** Moyen de paiement depuis son code d'API ("wave", "especes"...), null si vide. */
    public static MoyenPaiement moyenDepuisCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return MoyenPaiement.depuisCode(code);
        } catch (IllegalArgumentException e) {
            throw new RequeteInvalideException("MOYEN_INCONNU", "Moyen de paiement inconnu.", "moyen");
        }
    }

    public record PageHistorique(List<ReservationVue> elements, long total, int page, int taille,
                                 LocalDate du, LocalDate au, Map<StatutReservation, Long> parStatut,
                                 long encaisse, long nombreEncaisses, Map<String, Long> encaisseParMoyen) {
    }

    private final ReservationRepository reservations;
    private final Clock clock;

    public HistoriqueReservations(ReservationRepository reservations, Clock clock) {
        this.reservations = reservations;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageHistorique rechercher(Filtres f, int page, int taille) {
        LocalDate au = f.au() != null ? f.au() : LocalDate.now(clock);
        LocalDate du = f.du() != null ? f.du() : au.minusDays(29);
        if (du.isAfter(au)) {
            throw new RequeteInvalideException("PERIODE_INVALIDE", "La date de début doit précéder la date de fin.", "du");
        }
        if (ChronoUnit.DAYS.between(du, au) >= JOURS_MAX) {
            throw new RequeteInvalideException("PERIODE_TROP_LONGUE", "Choisissez une période d'un an au plus.", "du");
        }
        if (page < 0 || taille < 1 || taille > TAILLE_MAX) {
            throw new RequeteInvalideException("PAGE_INVALIDE", "Page ou taille de page invalide.", "taille");
        }
        Instant debut = du.atStartOfDay(Horloge.DAKAR).toInstant();
        Instant fin = au.plusDays(1).atStartOfDay(Horloge.DAKAR).toInstant();
        String recherche = f.recherche() == null || f.recherche().isBlank()
                ? null : "%" + f.recherche().trim().toLowerCase(Locale.ROOT) + "%";

        Page<Reservation> resultats = reservations.historique(debut, fin, f.terrainId(), f.moyen(), recherche, f.statut(),
                PageRequest.of(page, taille));

        Map<StatutReservation, Long> parStatut = new LinkedHashMap<>();
        for (StatutReservation s : StatutReservation.values()) {
            parStatut.put(s, 0L);
        }
        for (Object[] ligne : reservations.historiqueParStatut(debut, fin, f.terrainId(), f.moyen(), recherche)) {
            parStatut.put((StatutReservation) ligne[0], (Long) ligne[1]);
        }

        Map<String, Long> parMoyen = new LinkedHashMap<>();
        long encaisse = 0;
        long nombre = 0;
        for (Object[] ligne : reservations.historiqueEncaisse(debut, fin, f.terrainId(), f.moyen(), recherche, f.statut())) {
            long somme = ligne[2] == null ? 0 : ((Number) ligne[2]).longValue();
            if (ligne[0] != null) {
                parMoyen.put(((MoyenPaiement) ligne[0]).code(), somme);
            }
            encaisse += somme;
            nombre += (Long) ligne[1];
        }

        return new PageHistorique(resultats.map(ReservationVue::pourPersonnel).getContent(), resultats.getTotalElements(),
                page, taille, du, au, parStatut, encaisse, nombre, parMoyen);
    }
}
