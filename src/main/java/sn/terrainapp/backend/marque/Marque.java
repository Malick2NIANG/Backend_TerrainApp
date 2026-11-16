package sn.terrainapp.backend.marque;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identite du complexe (marque blanche), reglee a l'installation par les
 * variables MARQUE_* : nom, slogan, contacts. Servie aux deux apps et
 * reprise dans les messages (SMS, tickets).
 */
@ConfigurationProperties(prefix = "app.marque")
public record Marque(String nom, String slogan, String adresse, String telephone, String whatsapp, String email,
                     String ville) {

    public Marque {
        nom = nom == null || nom.isBlank() ? "TerrainApp" : nom.trim();
    }
}
