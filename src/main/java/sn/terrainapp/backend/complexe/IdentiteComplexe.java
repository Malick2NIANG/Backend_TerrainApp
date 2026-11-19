package sn.terrainapp.backend.complexe;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;

import sn.terrainapp.backend.complexe.ModeleComplexe.Complexe;
import sn.terrainapp.backend.marque.Marque;
import sn.terrainapp.backend.paiements.domaine.MoyenPaiement;

/**
 * Identite et regles du complexe installe, une seule source pour tout le
 * backend (SMS, controle des tickets, paiement, annulation) et pour les deux
 * apps (GET /marque). Ordre : dossier du complexe > variables MARQUE_* et
 * proprietes app.* > valeurs par defaut (TerrainApp, comportement d'origine).
 */
@Component
public class IdentiteComplexe {

    /** Moyens en ligne proposes sans dossier (comportement d'origine). */
    public static final List<MoyenPaiement> MOYENS_PAR_DEFAUT =
            List.of(MoyenPaiement.WAVE, MoyenPaiement.ORANGE_MONEY, MoyenPaiement.MIXX_YAS, MoyenPaiement.CARTE);

    private static final java.util.Map<MoyenPaiement, String> LIBELLES = java.util.Map.of(
            MoyenPaiement.WAVE, "Wave", MoyenPaiement.ORANGE_MONEY, "Orange Money",
            MoyenPaiement.MIXX_YAS, "Mixx by Yas", MoyenPaiement.CARTE, "carte");

    private final Optional<Complexe> complexe;
    private final Marque marque;
    private final int margeEntreeParDefaut;
    private final Vue vue;

    public IdentiteComplexe(ConfigurationComplexe configuration, Marque marque,
                            @Value("${app.ticket.marge-avant-minutes:30}") int margeEntreeParDefaut) {
        this.complexe = configuration.complexe();
        this.marque = marque;
        this.margeEntreeParDefaut = margeEntreeParDefaut;
        this.vue = construireVue();
    }

    // ------------------------------------------------------------ regles

    public String nom() {
        return depuis(c -> c.marque().nom(), marque.nom());
    }

    /** Moyens de paiement en ligne proposes par le complexe, dans l'ordre du dossier. */
    public List<MoyenPaiement> moyensEnLigne() {
        return complexe.map(c -> c.paiement().moyens().stream().map(MoyenPaiement::depuisCode).toList())
                .orElse(MOYENS_PAR_DEFAUT);
    }

    public Set<MoyenPaiement> moyensEnLigneAutorises() {
        return moyensEnLigne().stream().collect(Collectors.toCollection(() -> EnumSet.noneOf(MoyenPaiement.class)));
    }

    /** "Payez avec Wave ou Orange Money." */
    public String phraseMoyensEnLigne() {
        List<String> l = moyensEnLigne().stream().map(m -> m == MoyenPaiement.CARTE ? "par carte" : LIBELLES.get(m)).toList();
        String liste = l.size() == 1 ? l.get(0) : String.join(", ", l.subList(0, l.size() - 1)) + " ou " + l.get(l.size() - 1);
        return "Payez avec " + liste + ".";
    }

    /** Delai d'annulation en heures, absent = aucune regle (comportement d'origine). */
    public Optional<Integer> delaiAnnulationHeures() {
        return complexe.map(Complexe::reservation).map(ModeleComplexe.Reservation::delaiAnnulationHeures);
    }

    public int margeEntreeMinutes() {
        return complexe.map(Complexe::reservation).map(ModeleComplexe.Reservation::margeEntreeMinutes)
                .orElse(margeEntreeParDefaut);
    }

    public Vue vue() {
        return vue;
    }

    // ------------------------------------------------------------ GET /marque

    /**
     * Identite publique. Les 7 premiers champs sont ceux d'origine (les apps
     * deja deployees continuent de fonctionner) ; les autres sont absents
     * (null) quand rien n'est configure.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Vue(String nom, String slogan, String adresse, String telephone, String whatsapp, String email,
                      String ville,
                      boolean personnalise, String nomAccent, String initiales, String nomComplexe, String sousTitre,
                      Couleurs couleurs, String logoUrl, String imageAccueilUrl, String splashUrl, ModeleComplexe.Gps gps, ModeleComplexe.ReseauxSociaux reseauxSociaux,
                      ModeleComplexe.Support support, List<MoyenPaiement> moyensPaiement, Regles regles,
                      Documents documents, ModeleComplexe.Textes textes, String urlClient, String urlManager) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Couleurs(String principale, String foncee, String claire) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Regles(Integer pasCreneauMinutes, Integer delaiAnnulationHeures, int margeEntreeMinutes) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Documents(String nomSurTickets, String piedDeRecu) {
    }

    private Vue construireVue() {
        if (complexe.isEmpty()) {
            return new Vue(marque.nom(), marque.slogan(), marque.adresse(), marque.telephone(), marque.whatsapp(),
                    marque.email(), marque.ville(), false, null, null, null, null, null, null, null, null, null, null,
                    null, MOYENS_PAR_DEFAUT, new Regles(null, null, margeEntreeParDefaut), null, null, null, null);
        }
        Complexe c = complexe.get();
        var m = c.marque();
        var co = c.contact();
        var couleurs = new Couleurs(m.couleurs().principale().toUpperCase(), majuscules(m.couleurs().foncee()),
                majuscules(m.couleurs().claire()));
        var reservation = c.reservation();
        var regles = new Regles(reservation == null || reservation.pasCreneauMinutes() == null ? 60 : reservation.pasCreneauMinutes(),
                reservation == null ? null : reservation.delaiAnnulationHeures(), margeEntreeMinutes());
        return new Vue(m.nom(), m.slogan(), co.adresse(), co.telephone(), co.whatsapp(), co.email(), co.ville(),
                true, m.nomAccent(), m.initiales(), m.nomComplexe(), m.sousTitre(), couleurs,
                "/marque/" + m.logo(), fichier(m.imageAccueil()), fichier(m.splash()), co.gps(), co.reseauxSociaux(), c.support(), moyensEnLigne(), regles,
                new Documents(c.documents().nomSurTickets(), c.documents().piedDeRecu()), c.textes(),
                c.deploiement().urlClient(), c.deploiement().urlManager());
    }

    private static String fichier(String nom) {
        return nom == null || nom.isBlank() ? null : "/marque/" + nom;
    }

    private String depuis(Function<Complexe, String> lecture, String defaut) {
        return complexe.map(lecture).filter(s -> !s.isBlank()).orElse(defaut);
    }

    private static String majuscules(String s) {
        return s == null || s.isBlank() ? null : s.toUpperCase();
    }
}
