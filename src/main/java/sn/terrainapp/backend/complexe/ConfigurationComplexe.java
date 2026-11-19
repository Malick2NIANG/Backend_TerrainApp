package sn.terrainapp.backend.complexe;

import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import sn.terrainapp.backend.complexe.ModeleComplexe.Dossier;

/**
 * Dossier du complexe installe, lu UNE fois au demarrage depuis
 * APP_COMPLEXE_DOSSIER (en conteneur : /app/complexe, monte en lecture
 * seule). Variable vide : aucun dossier, la marque par defaut TerrainApp et
 * les variables MARQUE_* s'appliquent comme avant. Dossier invalide : le
 * backend refuse de demarrer, avec la liste des erreurs.
 */
@Component
public class ConfigurationComplexe {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationComplexe.class);

    private final Dossier dossier;

    public ConfigurationComplexe(@Value("${app.complexe.dossier:}") String chemin) {
        if (chemin == null || chemin.isBlank()) {
            this.dossier = null;
            return;
        }
        this.dossier = LectureComplexe.lire(Path.of(chemin.trim()));
        LOG.info("Complexe « {} » charge depuis {} ({} sport(s), {} terrain(s))", dossier.complexe().marque().nom(),
                chemin, dossier.sports().size(), dossier.terrains().size());
    }

    /** Dossier du complexe, absent sans configuration client. */
    public Optional<Dossier> dossier() {
        return Optional.ofNullable(dossier);
    }

    public Optional<ModeleComplexe.Complexe> complexe() {
        return dossier().map(Dossier::complexe);
    }
}
