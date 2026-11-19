package sn.terrainapp.backend.complexe;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * Contenu du dossier d'un complexe (clients/&lt;identifiant&gt;/), tel que lu sur
 * disque. Le format complet, avec ses regles, est decrit par le schema
 * outils/personnaliser/schema/complexe.schema.json ; la commande
 * {@code npm run client} le valide avant tout deploiement, et le backend
 * revalide l'essentiel au demarrage ({@link LectureComplexe}).
 *
 * Aucun secret ici : les codes d'activation du personnel, cles de paiement
 * et mots de passe passent par des variables d'environnement.
 */
public final class ModeleComplexe {

    private ModeleComplexe() {
    }

    /** complexe.json */
    public record Complexe(String identifiant, Marque marque, Contact contact, Support support,
                           Map<String, JsonNode> horaires, Paiement paiement, Reservation reservation,
                           Documents documents, Textes textes, Deploiement deploiement) {
    }

    /**
     * logo, imageAccueil, splash : fichiers de assets/ (servis par les fronts
     * sous /marque/). imageAccueil et splash : facultatifs.
     */
    public record Marque(String nom, String nomAccent, String initiales, String nomComplexe, String slogan,
                         String sousTitre, Couleurs couleurs, String logo, String imageAccueil, String splash) {
    }

    /** #RRGGBB. foncee et claire : facultatives (calculees depuis la principale). */
    public record Couleurs(String principale, String foncee, String claire) {
    }

    public record Contact(String telephone, String whatsapp, String email, String adresse, String ville, Gps gps,
                          ReseauxSociaux reseauxSociaux) {
    }

    public record Gps(Double latitude, Double longitude) {
    }

    public record ReseauxSociaux(String facebook, String instagram, String tiktok, String x, String youtube,
                                 String siteWeb) {
    }

    /** Contact du support (feuille "Besoin d'aide ?") ; a defaut, celui du complexe. */
    public record Support(String telephone, String email) {
    }

    /** moyens : codes des moyens de paiement EN LIGNE proposes (wave, orange-money, mixx-yas, carte). */
    public record Paiement(List<String> moyens) {
    }

    /**
     * pasCreneauMinutes : duree de creneau par defaut des terrains ; les
     * durees proposees en sont des multiples. delaiAnnulationHeures : absent =
     * aucune regle. margeEntreeMinutes : entree acceptee X min avant le creneau.
     */
    public record Reservation(Integer pasCreneauMinutes, Integer delaiAnnulationHeures, Integer margeEntreeMinutes) {
    }

    /** Nom imprime en tete des tickets et recus PDF, et phrase de pied du recu. */
    public record Documents(String nomSurTickets, String piedDeRecu) {
    }

    public record Textes(String accueilTitre, String accueilSousTitre, String aPropos) {
    }

    public record Deploiement(String urlClient, String urlManager, String version) {
    }

    // ------------------------------------------------------------ donnees/

    /** donnees/sports.json */
    public record Sport(String cle, String nom, Integer ordre) {
    }

    /**
     * donnees/terrains.json. sport : cle du sport. photos : fichiers de
     * assets/terrains/. horaires : propres au terrain (sinon ceux du complexe).
     */
    public record Terrain(String cle, String sport, String nom, String description, String localite, Gps gps,
                          String surface, Integer capacite, List<String> equipements, List<String> photos,
                          Long tarifHeure, List<Integer> dureesProposees, List<Plage> plages,
                          Map<String, JsonNode> horaires, Boolean publie, Integer ordre) {
    }

    /** jours : "lundi" ... "dimanche". debut / fin : "HH:mm" (fin <= debut : passe minuit). */
    public record Plage(String libelle, List<String> jours, String debut, String fin, Long tarifHeure) {
    }

    /** donnees/abonnements.json : abonnements en cours a reprendre (clubs, entreprises, academies). */
    public record Abonnement(String cle, String terrain, Client client, String frequence, List<String> jours,
                             String premiereSeance, Integer nombreSeances, String reglement, String moyenPaiement,
                             Long remise) {
    }

    public record Client(String nom, String telephone) {
    }

    /** donnees/annonces.json : annonces de lancement (popup de l'App Client). media : fichier de assets/annonces/. */
    public record Annonce(String cle, String titre, Boolean publiee, List<Slide> slides) {
    }

    public record Slide(String titre, String message, String media) {
    }

    /** donnees/personnel.json : comptes initiaux, SANS mot de passe (code d'activation a usage unique). */
    public record Membre(String identifiant, String role, String prenom, String nom, String telephone) {
    }

    /** Dossier complet, valide. */
    public record Dossier(Complexe complexe, List<Sport> sports, List<Terrain> terrains, List<Abonnement> abonnements,
                          List<Annonce> annonces, List<Membre> personnel) {
    }
}
