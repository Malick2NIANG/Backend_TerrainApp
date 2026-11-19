package sn.terrainapp.backend.complexe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sn.terrainapp.backend.complexe.LectureComplexe.ConfigurationInvalideException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Le dossier livre clients/exemple est valide, et chaque erreur typique est
 * signalee en francais avec le fichier et le champ.
 */
class LectureComplexeTest {

    private static final Path EXEMPLE = Path.of("clients/exemple");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path tmp;

    @Test
    void exempleLivreValide() {
        var d = LectureComplexe.lire(EXEMPLE);
        assertThat(d.complexe().marque().nom()).isEqualTo("Arena Exemple");
        assertThat(d.terrains()).hasSize(3);
        assertThat(d.personnel()).extracting(ModeleComplexe.Membre::role).contains("GERANT");
    }

    @Test
    void dossierAbsent() {
        assertThat(erreurs(tmp.resolve("inconnu"))).containsExactly("dossier introuvable : " + tmp.resolve("inconnu"));
    }

    @Test
    void champManquant() {
        assertThat(erreursApres("complexe.json", c -> ((ObjectNode) c.get("marque")).remove("nom")))
                .contains("complexe.json › marque.nom : champ manquant");
    }

    @Test
    void couleurInvalide() {
        assertThat(erreursApres("complexe.json", c -> ((ObjectNode) c.get("marque").get("couleurs")).put("principale", "vert")))
                .contains("complexe.json › marque.couleurs.principale : couleur invalide « vert » (format #RRGGBB, ex. #1F7A4D)");
    }

    @Test
    void logoAbsent() {
        assertThat(erreursApres("complexe.json", c -> ((ObjectNode) c.get("marque")).remove("logo")))
                .contains("complexe.json › marque.logo : champ manquant");
    }

    @Test
    void positionGpsInvalide() {
        assertThat(erreursApres("complexe.json", c -> ((ObjectNode) c.get("contact").get("gps")).put("latitude", 120)))
                .anyMatch(e -> e.startsWith("complexe.json › contact.gps.latitude : position GPS invalide"));
    }

    @Test
    void moyenDePaiementInconnu() {
        assertThat(erreursApres("complexe.json", c -> ((ArrayNode) c.get("paiement").get("moyens")).add("cheque")))
                .anyMatch(e -> e.startsWith("complexe.json › paiement.moyens : moyen inconnu « cheque »"));
    }

    @Test
    void tarifNegatif() {
        assertThat(erreursApres("donnees/terrains.json", t -> ((ObjectNode) t.get(0)).put("tarifHeure", -5000)))
                .contains("donnees/terrains.json › [0] (Terrain A).tarifHeure : tarif negatif (-5000 F CFA)");
    }

    @Test
    void dureesIncompatiblesAvecLePas() {
        List<String> e = erreursApres("complexe.json", c -> ((ObjectNode) c.get("reservation")).put("pasCreneauMinutes", 60));
        assertThat(e).contains("donnees/terrains.json › [0] (Terrain A).dureesProposees : duree 90 min incompatible "
                + "avec le pas des creneaux (60 min) : choisissez un multiple de 60");
    }

    @Test
    void sportInconnuEtHoraireIncomplet() {
        List<String> e = erreursApres("donnees/terrains.json", t -> {
            ((ObjectNode) t.get(1)).put("sport", "basket");
            ((ObjectNode) t.get(2).get("horaires")).remove("dimanche");
        });
        assertThat(e).contains("donnees/terrains.json › [1] (Grand terrain).sport : sport « basket » absent de donnees/sports.json");
        assertThat(e).anyMatch(x -> x.startsWith("donnees/terrains.json › [2] (Padel 1).horaires.dimanche : jour manquant"));
    }

    @Test
    void toutesLesErreursListeesEnUneFois() {
        List<String> e = erreursApres("complexe.json", c -> {
            ((ObjectNode) c.get("marque")).remove("nom");
            ((ObjectNode) c.get("contact")).put("email", "pas-un-email");
        });
        assertThat(e).hasSizeGreaterThanOrEqualTo(2);
    }

    // ------------------------------------------------------------ outils

    private List<String> erreurs(Path dossier) {
        try {
            LectureComplexe.lire(dossier);
        } catch (ConfigurationInvalideException ex) {
            return ex.erreurs();
        }
        return List.of();
    }

    /** Copie de l'exemple dont un fichier JSON est modifie. */
    private List<String> erreursApres(String fichier, Consumer<JsonNode> modification) {
        Path copie = tmp.resolve("dossier");
        copier(EXEMPLE, copie);
        Path f = copie.resolve(fichier);
        JsonNode n = JSON.readTree(f.toFile());
        modification.accept(n);
        JSON.writeValue(f.toFile(), n);
        assertThatThrownBy(() -> LectureComplexe.lire(copie)).isInstanceOf(ConfigurationInvalideException.class)
                .hasMessageContaining("Configuration du complexe invalide");
        return erreurs(copie);
    }

    private static void copier(Path source, Path cible) {
        try (Stream<Path> s = Files.walk(source)) {
            for (Path p : s.toList()) {
                Path c = cible.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(c);
                } else {
                    Files.copy(p, c);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
