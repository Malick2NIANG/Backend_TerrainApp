package sn.terrainapp.backend.complexe;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import sn.terrainapp.backend.abonnements.application.GestionAbonnements;
import sn.terrainapp.backend.abonnements.application.SaisieAbonnement;
import sn.terrainapp.backend.abonnements.domaine.Frequence;
import sn.terrainapp.backend.abonnements.domaine.ModeReglement;
import sn.terrainapp.backend.annonces.application.AnnonceSaisie;
import sn.terrainapp.backend.annonces.application.AnnonceService;
import sn.terrainapp.backend.annonces.domaine.StatutAnnonce;
import sn.terrainapp.backend.annonces.domaine.TypeMedia;
import sn.terrainapp.backend.auth.StaffUser;
import sn.terrainapp.backend.auth.StaffUserRepository;
import sn.terrainapp.backend.commun.audit.JournalActions;
import sn.terrainapp.backend.commun.temps.Horloge;
import sn.terrainapp.backend.complexe.ModeleComplexe.Dossier;
import sn.terrainapp.backend.paiements.domaine.MoyenPaiement;
import sn.terrainapp.backend.security.Role;
import sn.terrainapp.backend.terrains.application.GestionTerrains;
import sn.terrainapp.backend.terrains.application.GestionTerrains.SaisieHoraire;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Charge les donnees initiales du dossier du complexe (donnees/ et horaires
 * de complexe.json) au demarrage. Idempotent : chaque element est note dans
 * elements_configures sous sa cle ; un element deja charge n'est JAMAIS
 * recree ni modifie (le gerant a pu le changer dans le Manager). Rejouer le
 * chargement ajoute seulement les elements nouveaux du dossier.
 *
 * Tout ou rien : une transaction pour l'ensemble, le backend refuse de
 * demarrer si un element ne peut pas etre cree (message en francais).
 *
 * Comptes du personnel : jamais de mot de passe. Chaque compte est cree
 * avec l'empreinte BCrypt de son code d'activation a usage unique
 * (APP_COMPLEXE_ACTIVATIONS, produite par npm run client) et doit choisir son
 * mot de passe a la premiere connexion ; le code ne sert alors plus a rien.
 */
@Component
@Order(2)
public class ChargementDonneesInitiales implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ChargementDonneesInitiales.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    enum Type { HORAIRES, SPORT, TERRAIN, ANNONCE, ABONNEMENT, PERSONNEL }

    /** Bilan d'un chargement : elements crees et deja presents (ignores). */
    public record Bilan(int crees, int dejaPresents) {
    }

    private final ConfigurationComplexe configuration;
    private final GestionTerrains terrains;
    private final AnnonceService annonces;
    private final GestionAbonnements abonnements;
    private final StaffUserRepository comptes;
    private final JournalActions journal;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final String activations;

    public ChargementDonneesInitiales(ConfigurationComplexe configuration, GestionTerrains terrains,
                                      AnnonceService annonces, GestionAbonnements abonnements,
                                      StaffUserRepository comptes, JournalActions journal, JdbcTemplate jdbc,
                                      TransactionTemplate transaction,
                                      @Value("${app.complexe.activations:}") String activations) {
        this.configuration = configuration;
        this.terrains = terrains;
        this.annonces = annonces;
        this.abonnements = abonnements;
        this.comptes = comptes;
        this.journal = journal;
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.activations = activations == null ? "" : activations.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        configuration.dossier().ifPresent(d -> {
            Bilan b = charger(d);
            LOG.info("Donnees du complexe : {} element(s) cree(s), {} deja present(s)", b.crees(), b.dejaPresents());
        });
    }

    /** Charge le dossier (rejouable). */
    public Bilan charger(Dossier d) {
        return transaction.execute(statut -> new Chargement(d).executer());
    }

    /** Un passage de chargement. */
    private final class Chargement {
        private final Dossier d;
        private final Map<String, Long> sports = new HashMap<>();
        private final Map<String, Long> terrainsCharges = new HashMap<>();
        private int crees;
        private int dejaPresents;

        Chargement(Dossier d) {
            this.d = d;
        }

        Bilan executer() {
            horaires();
            sports();
            terrains();
            annonces();
            abonnements();
            personnel();
            return new Bilan(crees, dejaPresents);
        }

        private void horaires() {
            if (dejaCharge(Type.HORAIRES, "complexe").isPresent()) {
                dejaPresents++;
                return;
            }
            terrains.modifierHoraires(saisiesHoraires(d.complexe().horaires()));
            noter(Type.HORAIRES, "complexe", null);
        }

        private void sports() {
            for (var s : d.sports()) {
                Optional<Long> id = dejaCharge(Type.SPORT, s.cle());
                if (id.isPresent()) {
                    sports.put(s.cle(), id.get());
                    dejaPresents++;
                    continue;
                }
                var vue = terrains.creerSport(new GestionTerrains.SaisieSport(s.nom(), s.ordre() == null ? 0 : s.ordre(), true));
                sports.put(s.cle(), vue.id());
                noter(Type.SPORT, s.cle(), vue.id());
            }
        }

        private void terrains() {
            int pas = d.complexe().reservation() == null || d.complexe().reservation().pasCreneauMinutes() == null
                    ? 60 : d.complexe().reservation().pasCreneauMinutes();
            for (var t : d.terrains()) {
                Optional<Long> id = dejaCharge(Type.TERRAIN, t.cle());
                if (id.isPresent()) {
                    terrainsCharges.put(t.cle(), id.get());
                    dejaPresents++;
                    continue;
                }
                List<Integer> durees = t.dureesProposees() == null || t.dureesProposees().isEmpty()
                        ? List.of(pas) : t.dureesProposees();
                List<GestionTerrains.SaisiePlage> plages = t.plages() == null ? List.of() : t.plages().stream()
                        .map(p -> new GestionTerrains.SaisiePlage(p.libelle(), numeros(p.jours()),
                                LocalTime.parse(p.debut()), LocalTime.parse(p.fin()), p.tarifHeure()))
                        .toList();
                List<String> photos = t.photos() == null ? List.of()
                        : t.photos().stream().map(f -> "/marque/terrains/" + f).toList();
                var saisie = new GestionTerrains.SaisieTerrain(sports.get(t.sport()), t.nom(), t.description(),
                        t.localite(), t.gps() == null ? null : t.gps().latitude(),
                        t.gps() == null ? null : t.gps().longitude(), t.surface(), t.capacite(), t.equipements(),
                        photos, durees.stream().min(Integer::compare).orElse(pas), t.tarifHeure(), plages,
                        t.publie() == null || t.publie(), t.ordre() == null ? 0 : t.ordre(),
                        t.horaires() == null || t.horaires().isEmpty() ? List.of() : saisiesHoraires(t.horaires()),
                        durees);
                var vue = terrains.creerTerrain(saisie);
                terrainsCharges.put(t.cle(), vue.id());
                noter(Type.TERRAIN, t.cle(), vue.id());
            }
        }

        private void annonces() {
            for (var a : d.annonces()) {
                if (dejaCharge(Type.ANNONCE, a.cle()).isPresent()) {
                    dejaPresents++;
                    continue;
                }
                List<AnnonceSaisie.Slide> slides = a.slides().stream().map(s -> {
                    boolean media = s.media() != null && !s.media().isBlank();
                    boolean video = media && (s.media().endsWith(".mp4") || s.media().endsWith(".webm"));
                    return new AnnonceSaisie.Slide(s.titre(), s.message(), media ? "/marque/annonces/" + s.media() : null,
                            video ? TypeMedia.VIDEO : TypeMedia.IMAGE);
                }).toList();
                var vue = annonces.creer(new AnnonceSaisie(a.titre(), slides, null));
                if (a.publiee() == null || a.publiee()) {
                    vue = annonces.changerStatut(Long.parseLong(vue.id()), StatutAnnonce.ACTIF, vue.version());
                }
                noter(Type.ANNONCE, a.cle(), Long.parseLong(vue.id()));
            }
        }

        private void abonnements() {
            for (var a : d.abonnements()) {
                if (dejaCharge(Type.ABONNEMENT, a.cle()).isPresent()) {
                    dejaPresents++;
                    continue;
                }
                Instant premiere = LocalDateTime.parse(a.premiereSeance()).atZone(Horloge.DAKAR).toInstant();
                Frequence frequence = Frequence.valueOf(a.frequence());
                Set<Integer> jours = frequence == Frequence.HEBDOMADAIRE && a.jours() != null && !a.jours().isEmpty()
                        ? numeros(a.jours()) : null;
                var vue = abonnements.creer(new SaisieAbonnement(terrainsCharges.get(a.terrain()), premiere, frequence,
                        a.nombreSeances(), a.client().nom(), a.client().telephone(), ModeReglement.valueOf(a.reglement()),
                        a.moyenPaiement() == null ? null : MoyenPaiement.depuisCode(a.moyenPaiement()),
                        a.remise() == null ? 0 : a.remise(), jours, null));
                noter(Type.ABONNEMENT, a.cle(), vue.id());
            }
        }

        private void personnel() {
            Map<String, String> empreintes = empreintesActivation();
            for (var m : d.personnel()) {
                if (dejaCharge(Type.PERSONNEL, m.identifiant()).isPresent()) {
                    dejaPresents++;
                    continue;
                }
                Optional<StaffUser> existant = comptes.findByUsernameIgnoreCase(m.identifiant());
                if (existant.isPresent()) {
                    // Compte deja cree a la main : on le laisse tel quel.
                    noter(Type.PERSONNEL, m.identifiant(), existant.get().getId());
                    dejaPresents++;
                    continue;
                }
                String empreinte = empreintes.get(m.identifiant());
                if (empreinte == null || !empreinte.startsWith("$2")) {
                    throw new IllegalStateException("Compte « " + m.identifiant() + " » (donnees/personnel.json) : "
                            + "code d'activation absent de APP_COMPLEXE_ACTIVATIONS. Relancez npm run client puis "
                            + "redeployez avec le .env produit.");
                }
                StaffUser u = new StaffUser(m.identifiant(), empreinte, Role.valueOf(m.role()), true);
                u.setFirstName(m.prenom());
                u.setLastName(m.nom());
                u.setPhone(m.telephone());
                u = comptes.save(u);
                journal.enregistrerEnTantQue("systeme", JournalActions.Domaine.PERSONNEL, "CREATION", String.valueOf(u.getId()),
                        "a créé le compte " + m.identifiant() + " (" + m.role().toLowerCase()
                                + ", mot de passe à choisir à la première connexion)");
                noter(Type.PERSONNEL, m.identifiant(), u.getId());
            }
        }

        private Optional<Long> dejaCharge(Type type, String cle) {
            List<Long> ids = jdbc.queryForList("select entite_id from elements_configures where type = ? and cle = ?",
                    Long.class, type.name(), cle);
            if (ids.isEmpty()) {
                return Optional.empty();
            }
            // Ligne presente sans identifiant (horaires) : 0 = deja charge.
            return Optional.of(ids.get(0) == null ? 0L : ids.get(0));
        }

        private void noter(Type type, String cle, Long id) {
            jdbc.update("insert into elements_configures (type, cle, entite_id, charge_le) values (?, ?, ?, ?)",
                    type.name(), cle, id, Timestamp.from(Instant.now()));
            crees++;
        }
    }

    /** APP_COMPLEXE_ACTIVATIONS : base64 d'un objet JSON { identifiant: empreinte BCrypt }. */
    private Map<String, String> empreintesActivation() {
        if (activations.isEmpty()) {
            return Map.of();
        }
        try {
            JsonNode n = JSON.readTree(new String(Base64.getDecoder().decode(activations), StandardCharsets.UTF_8));
            Map<String, String> m = new HashMap<>();
            n.properties().forEach(e -> m.put(e.getKey(), e.getValue().asString()));
            return m;
        } catch (RuntimeException e) {
            throw new IllegalStateException("APP_COMPLEXE_ACTIVATIONS illisible : relancez npm run client.", e);
        }
    }

    /** Horaires du dossier ("ferme" ou { ouverture, fermeture }) -> 7 saisies. */
    static List<SaisieHoraire> saisiesHoraires(Map<String, JsonNode> horaires) {
        List<SaisieHoraire> l = new ArrayList<>();
        for (String jour : LectureComplexe.JOURS) {
            JsonNode n = horaires.get(jour);
            int numero = LectureComplexe.numeroJour(jour);
            if (n.isString()) {
                l.add(new SaisieHoraire(numero, false, LocalTime.of(8, 0), LocalTime.of(23, 0)));
            } else {
                l.add(new SaisieHoraire(numero, true, LocalTime.parse(n.get("ouverture").asString()),
                        LocalTime.parse(n.get("fermeture").asString())));
            }
        }
        return l;
    }

    private static Set<Integer> numeros(List<String> jours) {
        return jours.stream().map(LectureComplexe::numeroJour).collect(Collectors.toSet());
    }
}
