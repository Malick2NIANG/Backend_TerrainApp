package sn.terrainapp.backend.complexe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import sn.terrainapp.backend.complexe.ModeleComplexe.Abonnement;
import sn.terrainapp.backend.complexe.ModeleComplexe.Annonce;
import sn.terrainapp.backend.complexe.ModeleComplexe.Complexe;
import sn.terrainapp.backend.complexe.ModeleComplexe.Dossier;
import sn.terrainapp.backend.complexe.ModeleComplexe.Gps;
import sn.terrainapp.backend.complexe.ModeleComplexe.Membre;
import sn.terrainapp.backend.complexe.ModeleComplexe.Plage;
import sn.terrainapp.backend.complexe.ModeleComplexe.Sport;
import sn.terrainapp.backend.complexe.ModeleComplexe.Terrain;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.type.TypeReference;

/**
 * Lecture et validation du dossier d'un complexe. Toutes les erreurs sont
 * collectees (pas d'arret a la premiere), avec un message en francais qui
 * dit OU (fichier et champ) et QUOI corriger. Memes regles que le schema de
 * la commande npm run client : le backend refuse de demarrer sur un dossier
 * modifie a la main et devenu invalide.
 */
public final class LectureComplexe {

    public static final List<String> JOURS = List.of("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche");
    public static final Set<String> MOYENS_EN_LIGNE = Set.of("wave", "orange-money", "mixx-yas", "carte");
    public static final Set<Integer> PAS_AUTORISES = Set.of(30, 60, 90, 120);
    public static final Set<String> ROLES = Set.of("GERANT", "ACCUEIL", "CONTROLEUR", "ADMIN");
    public static final int DUREE_MIN = 30;
    public static final int DUREE_MAX = 180;

    private static final Pattern COULEUR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern CLE = Pattern.compile("^[a-z0-9][a-z0-9-]{0,59}$");
    private static final Pattern IDENTIFIANT = Pattern.compile("^[a-z0-9][a-z0-9-]{1,39}$");
    private static final Pattern IDENTIFIANT_PERSONNEL = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,39}$");
    private static final Pattern FICHIER = Pattern.compile("^[a-z0-9][a-z0-9_-]*\\.(jpg|jpeg|png|webp|svg|mp4|webm)$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TELEPHONE = Pattern.compile("^\\+?[0-9 ]{9,20}$");

    private static final JsonMapper JSON = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    /** Erreurs de configuration, toutes listees dans le message. */
    public static final class ConfigurationInvalideException extends IllegalStateException {
        private final transient List<String> erreurs;

        public ConfigurationInvalideException(Path dossier, List<String> erreurs) {
            super("Configuration du complexe invalide (" + dossier + ") :\n  - " + String.join("\n  - ", erreurs));
            this.erreurs = List.copyOf(erreurs);
        }

        public List<String> erreurs() {
            return erreurs;
        }
    }

    private final Path dossier;
    private final List<String> erreurs = new ArrayList<>();

    private LectureComplexe(Path dossier) {
        this.dossier = dossier;
    }

    /** Lit et valide le dossier, ou leve {@link ConfigurationInvalideException} avec toutes les erreurs. */
    public static Dossier lire(Path dossier) {
        LectureComplexe l = new LectureComplexe(dossier);
        Dossier d = l.charger();
        if (!l.erreurs.isEmpty()) {
            throw new ConfigurationInvalideException(dossier, l.erreurs);
        }
        return d;
    }

    private Dossier charger() {
        if (!Files.isDirectory(dossier)) {
            erreurs.add("dossier introuvable : " + dossier);
            return null;
        }
        Complexe c = fichier("complexe.json", new TypeReference<Complexe>() { }, true);
        List<Sport> sports = liste("donnees/sports.json", new TypeReference<List<Sport>>() { });
        List<Terrain> terrains = liste("donnees/terrains.json", new TypeReference<List<Terrain>>() { });
        List<Abonnement> abonnements = liste("donnees/abonnements.json", new TypeReference<List<Abonnement>>() { });
        List<Annonce> annonces = liste("donnees/annonces.json", new TypeReference<List<Annonce>>() { });
        List<Membre> personnel = liste("donnees/personnel.json", new TypeReference<List<Membre>>() { });
        if (c == null) {
            return null;
        }
        int pas = verifierComplexe(c);
        Set<String> clesSports = verifierSports(sports);
        Set<String> clesTerrains = verifierTerrains(terrains, clesSports, pas);
        verifierAbonnements(abonnements, clesTerrains);
        verifierAnnonces(annonces);
        verifierPersonnel(personnel);
        return new Dossier(c, sports, terrains, abonnements, annonces, personnel);
    }

    // ------------------------------------------------------------ lecture

    private <T> T fichier(String chemin, TypeReference<T> type, boolean obligatoire) {
        Path p = dossier.resolve(chemin);
        if (!Files.isRegularFile(p)) {
            if (obligatoire) {
                erreurs.add(chemin + " : fichier manquant");
            }
            return null;
        }
        try {
            return JSON.readValue(p.toFile(), type);
        } catch (JacksonException e) {
            erreurs.add(chemin + " : JSON illisible ou champ du mauvais type (" + e.getOriginalMessage() + ")");
            return null;
        }
    }

    private <T> List<T> liste(String chemin, TypeReference<List<T>> type) {
        List<T> l = fichier(chemin, type, false);
        return l == null ? List.of() : l;
    }

    // ------------------------------------------------------------ complexe.json

    /** Renvoie le pas des creneaux (60 par defaut). */
    private int verifierComplexe(Complexe c) {
        String f = "complexe.json";
        if (vide(c.identifiant())) {
            manquant(f, "identifiant");
        } else if (!IDENTIFIANT.matcher(c.identifiant()).matches()) {
            erreur(f, "identifiant", "« " + c.identifiant() + " » invalide (minuscules, chiffres et tirets, 2 a 40 caracteres)");
        }
        if (c.marque() == null) {
            manquant(f, "marque");
        } else {
            requis(f, "marque.nom", c.marque().nom(), 40);
            requis(f, "marque.nomComplexe", c.marque().nomComplexe(), 80);
            requis(f, "marque.slogan", c.marque().slogan(), 120);
            if (!vide(c.marque().nomAccent()) && !vide(c.marque().nom())
                    && !c.marque().nom().toLowerCase().endsWith(c.marque().nomAccent().toLowerCase())) {
                erreur(f, "marque.nomAccent", "doit etre la fin du nom (« " + c.marque().nom() + " »)");
            }
            if (!vide(c.marque().initiales()) && c.marque().initiales().length() > 3) {
                erreur(f, "marque.initiales", "3 caracteres au maximum");
            }
            if (vide(c.marque().logo())) {
                manquant(f, "marque.logo");
            } else if (!FICHIER.matcher(c.marque().logo()).matches()) {
                erreur(f, "marque.logo", "nom de fichier invalide « " + c.marque().logo() + " » (ex. logo.png)");
            }
            for (String image : java.util.Arrays.asList(c.marque().imageAccueil(), c.marque().splash())) {
                if (!vide(image) && !FICHIER.matcher(image).matches()) {
                    erreur(f, "marque", "nom de fichier d'image invalide « " + image + " »");
                }
            }
            if (c.marque().couleurs() == null) {
                manquant(f, "marque.couleurs");
            } else {
                couleur(f, "marque.couleurs.principale", c.marque().couleurs().principale(), true);
                couleur(f, "marque.couleurs.foncee", c.marque().couleurs().foncee(), false);
                couleur(f, "marque.couleurs.claire", c.marque().couleurs().claire(), false);
            }
        }
        if (c.contact() == null) {
            manquant(f, "contact");
        } else {
            requis(f, "contact.adresse", c.contact().adresse(), 200);
            requis(f, "contact.ville", c.contact().ville(), 80);
            telephone(f, "contact.telephone", c.contact().telephone(), true);
            telephone(f, "contact.whatsapp", c.contact().whatsapp(), false);
            email(f, "contact.email", c.contact().email(), true);
            gps(f, "contact.gps", c.contact().gps(), true);
        }
        if (c.support() != null) {
            telephone(f, "support.telephone", c.support().telephone(), false);
            email(f, "support.email", c.support().email(), false);
        }
        horaires(f, "horaires", c.horaires(), true);
        if (c.paiement() == null || c.paiement().moyens() == null || c.paiement().moyens().isEmpty()) {
            erreur(f, "paiement.moyens", "indiquez au moins un moyen de paiement en ligne (" + String.join(", ", trie(MOYENS_EN_LIGNE)) + ")");
        } else {
            for (String m : c.paiement().moyens()) {
                if (!MOYENS_EN_LIGNE.contains(m)) {
                    erreur(f, "paiement.moyens", "moyen inconnu « " + m + " » (attendu : " + String.join(", ", trie(MOYENS_EN_LIGNE)) + ")");
                }
            }
            if (new HashSet<>(c.paiement().moyens()).size() != c.paiement().moyens().size()) {
                erreur(f, "paiement.moyens", "un moyen est cite deux fois");
            }
        }
        int pas = 60;
        if (c.reservation() != null) {
            Integer p = c.reservation().pasCreneauMinutes();
            if (p != null) {
                if (PAS_AUTORISES.contains(p)) {
                    pas = p;
                } else {
                    erreur(f, "reservation.pasCreneauMinutes", p + " invalide (attendu : 30, 60, 90 ou 120)");
                }
            }
            Integer d = c.reservation().delaiAnnulationHeures();
            if (d != null && (d < 1 || d > 168)) {
                erreur(f, "reservation.delaiAnnulationHeures", d + " invalide (1 a 168 heures, ou retirez le champ pour aucune regle)");
            }
            Integer m = c.reservation().margeEntreeMinutes();
            if (m != null && (m < 0 || m > 240)) {
                erreur(f, "reservation.margeEntreeMinutes", m + " invalide (0 a 240 minutes)");
            }
        }
        if (c.documents() == null) {
            manquant(f, "documents");
        } else {
            requis(f, "documents.nomSurTickets", c.documents().nomSurTickets(), 40);
        }
        if (c.deploiement() == null) {
            manquant(f, "deploiement");
        } else {
            url(f, "deploiement.urlClient", c.deploiement().urlClient());
            url(f, "deploiement.urlManager", c.deploiement().urlManager());
        }
        return pas;
    }

    // ------------------------------------------------------------ donnees/

    private Set<String> verifierSports(List<Sport> sports) {
        String f = "donnees/sports.json";
        Set<String> cles = new HashSet<>();
        Set<String> noms = new HashSet<>();
        for (int i = 0; i < sports.size(); i++) {
            Sport s = sports.get(i);
            String ici = "[" + i + "]";
            cle(f, ici, s.cle(), cles);
            requis(f, ici + ".nom", s.nom(), 60);
            if (!vide(s.nom()) && !noms.add(s.nom().trim().toLowerCase())) {
                erreur(f, ici + ".nom", "sport « " + s.nom() + " » en double");
            }
        }
        return cles;
    }

    private Set<String> verifierTerrains(List<Terrain> terrains, Set<String> sports, int pas) {
        String f = "donnees/terrains.json";
        Set<String> cles = new HashSet<>();
        for (int i = 0; i < terrains.size(); i++) {
            Terrain t = terrains.get(i);
            String ici = "[" + i + "]" + (vide(t.nom()) ? "" : " (" + t.nom() + ")");
            cle(f, ici, t.cle(), cles);
            requis(f, ici + ".nom", t.nom(), 80);
            if (vide(t.sport())) {
                manquant(f, ici + ".sport");
            } else if (!sports.contains(t.sport())) {
                erreur(f, ici + ".sport", "sport « " + t.sport() + " » absent de donnees/sports.json");
            }
            if (t.capacite() == null || t.capacite() < 1 || t.capacite() > 100) {
                erreur(f, ici + ".capacite", "nombre de joueurs attendu, de 1 a 100");
            }
            if (t.tarifHeure() == null) {
                manquant(f, ici + ".tarifHeure");
            } else if (t.tarifHeure() < 0) {
                erreur(f, ici + ".tarifHeure", "tarif negatif (" + t.tarifHeure() + " F CFA)");
            }
            durees(f, ici + ".dureesProposees", t.dureesProposees(), pas);
            if (t.photos() != null) {
                if (t.photos().size() > 10) {
                    erreur(f, ici + ".photos", "10 photos au maximum");
                }
                t.photos().forEach(p -> fichierMedia(f, ici + ".photos", p));
            }
            gps(f, ici + ".gps", t.gps(), false);
            horaires(f, ici + ".horaires", t.horaires(), false);
            List<Plage> plages = t.plages() == null ? List.of() : t.plages();
            for (int j = 0; j < plages.size(); j++) {
                Plage p = plages.get(j);
                String ip = ici + ".plages[" + j + "]";
                requis(f, ip + ".libelle", p.libelle(), 60);
                jours(f, ip + ".jours", p.jours(), true);
                LocalTime d = heure(f, ip + ".debut", p.debut());
                LocalTime fin = heure(f, ip + ".fin", p.fin());
                if (d != null && d.equals(fin)) {
                    erreur(f, ip, "le debut et la fin doivent etre differents");
                }
                if (p.tarifHeure() == null || p.tarifHeure() < 0) {
                    erreur(f, ip + ".tarifHeure", p.tarifHeure() == null ? "champ manquant" : "tarif negatif (" + p.tarifHeure() + " F CFA)");
                }
            }
        }
        return cles;
    }

    private void verifierAbonnements(List<Abonnement> abonnements, Set<String> terrains) {
        String f = "donnees/abonnements.json";
        Set<String> cles = new HashSet<>();
        for (int i = 0; i < abonnements.size(); i++) {
            Abonnement a = abonnements.get(i);
            String ici = "[" + i + "]";
            cle(f, ici, a.cle(), cles);
            if (vide(a.terrain()) || !terrains.contains(a.terrain())) {
                erreur(f, ici + ".terrain", vide(a.terrain()) ? "champ manquant"
                        : "terrain « " + a.terrain() + " » absent de donnees/terrains.json");
            }
            if (a.client() == null) {
                manquant(f, ici + ".client");
            } else {
                requis(f, ici + ".client.nom", a.client().nom(), 80);
                telephone(f, ici + ".client.telephone", a.client().telephone(), true);
            }
            if (!"HEBDOMADAIRE".equals(a.frequence()) && !"MENSUELLE".equals(a.frequence())) {
                erreur(f, ici + ".frequence", "attendu : HEBDOMADAIRE ou MENSUELLE");
            }
            jours(f, ici + ".jours", a.jours(), false);
            try {
                if (vide(a.premiereSeance())) {
                    manquant(f, ici + ".premiereSeance");
                } else {
                    LocalDateTime.parse(a.premiereSeance());
                }
            } catch (DateTimeParseException e) {
                erreur(f, ici + ".premiereSeance", "date et heure attendues, ex. 2026-12-01T19:00 (heure du complexe)");
            }
            if (a.nombreSeances() == null || a.nombreSeances() < 2 || a.nombreSeances() > 52) {
                erreur(f, ici + ".nombreSeances", "de 2 a 52 seances");
            }
            if (!"AVANCE".equals(a.reglement()) && !"SEANCE".equals(a.reglement())) {
                erreur(f, ici + ".reglement", "attendu : AVANCE (paye d'avance) ou SEANCE (a chaque seance)");
            } else if ("AVANCE".equals(a.reglement()) && vide(a.moyenPaiement())) {
                erreur(f, ici + ".moyenPaiement", "obligatoire pour un reglement d'avance");
            }
            if (a.remise() != null && a.remise() < 0) {
                erreur(f, ici + ".remise", "remise negative");
            }
        }
    }

    private void verifierAnnonces(List<Annonce> annonces) {
        String f = "donnees/annonces.json";
        Set<String> cles = new HashSet<>();
        for (int i = 0; i < annonces.size(); i++) {
            Annonce a = annonces.get(i);
            String ici = "[" + i + "]";
            cle(f, ici, a.cle(), cles);
            requis(f, ici + ".titre", a.titre(), 80);
            if (a.slides() == null || a.slides().isEmpty() || a.slides().size() > 10) {
                erreur(f, ici + ".slides", "de 1 a 10 slides");
                continue;
            }
            for (int j = 0; j < a.slides().size(); j++) {
                var s = a.slides().get(j);
                requis(f, ici + ".slides[" + j + "].titre", s.titre(), 80);
                if (s.message() != null && s.message().length() > 300) {
                    erreur(f, ici + ".slides[" + j + "].message", "300 caracteres au maximum");
                }
                if (!vide(s.media())) {
                    fichierMedia(f, ici + ".slides[" + j + "].media", s.media());
                }
            }
        }
    }

    private void verifierPersonnel(List<Membre> personnel) {
        String f = "donnees/personnel.json";
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < personnel.size(); i++) {
            Membre m = personnel.get(i);
            String ici = "[" + i + "]";
            if (vide(m.identifiant()) || !IDENTIFIANT_PERSONNEL.matcher(m.identifiant()).matches()) {
                erreur(f, ici + ".identifiant", "minuscules, chiffres, point ou tiret, 3 a 40 caracteres");
            } else if (!ids.add(m.identifiant())) {
                erreur(f, ici + ".identifiant", "« " + m.identifiant() + " » en double");
            }
            if (m.role() == null || !ROLES.contains(m.role())) {
                erreur(f, ici + ".role", "attendu : " + String.join(", ", trie(ROLES)));
            }
            telephone(f, ici + ".telephone", m.telephone(), false);
        }
    }

    // ------------------------------------------------------------ regles

    private void durees(String f, String champ, List<Integer> durees, int pas) {
        if (durees == null || durees.isEmpty()) {
            return;
        }
        if (durees.size() > 6) {
            erreur(f, champ, "6 durees au maximum");
        }
        for (Integer d : durees) {
            if (d == null || d < DUREE_MIN || d > DUREE_MAX) {
                erreur(f, champ, "duree " + d + " min hors limites (30 min a 3 h)");
            } else if (d % pas != 0) {
                erreur(f, champ, "duree " + d + " min incompatible avec le pas des creneaux (" + pas
                        + " min) : choisissez un multiple de " + pas);
            }
        }
    }

    private void horaires(String f, String champ, Map<String, JsonNode> h, boolean obligatoire) {
        if (h == null || h.isEmpty()) {
            if (obligatoire) {
                manquant(f, champ);
            }
            return;
        }
        for (String jour : JOURS) {
            JsonNode n = h.get(jour);
            if (n == null) {
                erreur(f, champ + "." + jour, "jour manquant (les 7 jours sont attendus ; \"ferme\" pour un jour de fermeture)");
            } else if (n.isString()) {
                if (!"ferme".equals(n.asString())) {
                    erreur(f, champ + "." + jour, "attendu \"ferme\" ou { \"ouverture\": \"08:00\", \"fermeture\": \"23:00\" }");
                }
            } else {
                LocalTime o = heure(f, champ + "." + jour + ".ouverture", texte(n, "ouverture"));
                LocalTime fe = heure(f, champ + "." + jour + ".fermeture", texte(n, "fermeture"));
                if (o != null && o.equals(fe)) {
                    erreur(f, champ + "." + jour, "l'ouverture et la fermeture doivent etre differentes");
                }
            }
        }
        h.keySet().stream().filter(j -> !JOURS.contains(j))
                .forEach(j -> erreur(f, champ + "." + j, "jour inconnu (attendu : " + String.join(", ", JOURS) + ")"));
    }

    private void jours(String f, String champ, List<String> jours, boolean obligatoire) {
        if (jours == null || jours.isEmpty()) {
            if (obligatoire) {
                manquant(f, champ);
            }
            return;
        }
        for (String j : jours) {
            if (!JOURS.contains(j)) {
                erreur(f, champ, "jour inconnu « " + j + " » (attendu : " + String.join(", ", JOURS) + ")");
            }
        }
    }

    private LocalTime heure(String f, String champ, String valeur) {
        if (vide(valeur)) {
            manquant(f, champ);
            return null;
        }
        try {
            if (!valeur.matches("^\\d{2}:\\d{2}$")) {
                throw new DateTimeParseException("format", valeur, 0);
            }
            return LocalTime.parse(valeur);
        } catch (DateTimeParseException e) {
            erreur(f, champ, "heure invalide « " + valeur + " » (format HH:mm, 00:00 a 23:59)");
            return null;
        }
    }

    private void gps(String f, String champ, Gps gps, boolean obligatoire) {
        if (gps == null) {
            if (obligatoire) {
                manquant(f, champ);
            }
            return;
        }
        if (gps.latitude() == null || gps.latitude() < -90 || gps.latitude() > 90) {
            erreur(f, champ + ".latitude", "position GPS invalide (latitude de -90 a 90, ex. 14.7167)");
        }
        if (gps.longitude() == null || gps.longitude() < -180 || gps.longitude() > 180) {
            erreur(f, champ + ".longitude", "position GPS invalide (longitude de -180 a 180, ex. -17.4677)");
        }
        if (gps.latitude() != null && gps.longitude() != null && gps.latitude() == 0 && gps.longitude() == 0) {
            erreur(f, champ, "position GPS 0,0 (en plein ocean) : position non renseignee ?");
        }
    }

    private void couleur(String f, String champ, String valeur, boolean obligatoire) {
        if (vide(valeur)) {
            if (obligatoire) {
                manquant(f, champ);
            }
        } else if (!COULEUR.matcher(valeur).matches()) {
            erreur(f, champ, "couleur invalide « " + valeur + " » (format #RRGGBB, ex. #1F7A4D)");
        }
    }

    private void telephone(String f, String champ, String valeur, boolean obligatoire) {
        if (vide(valeur)) {
            if (obligatoire) {
                manquant(f, champ);
            }
        } else if (!TELEPHONE.matcher(valeur).matches()) {
            erreur(f, champ, "numero invalide « " + valeur + " » (ex. +221 77 000 00 00)");
        }
    }

    private void email(String f, String champ, String valeur, boolean obligatoire) {
        if (vide(valeur)) {
            if (obligatoire) {
                manquant(f, champ);
            }
        } else if (!EMAIL.matcher(valeur).matches()) {
            erreur(f, champ, "adresse e-mail invalide « " + valeur + " »");
        }
    }

    private void url(String f, String champ, String valeur) {
        if (vide(valeur)) {
            manquant(f, champ);
        } else if (!valeur.matches("^https?://[^\\s/]+.*$")) {
            erreur(f, champ, "URL invalide « " + valeur + " » (ex. https://reserver.mon-complexe.sn)");
        }
    }

    private void fichierMedia(String f, String champ, String valeur) {
        if (vide(valeur) || !FICHIER.matcher(valeur).matches()) {
            erreur(f, champ, "nom de fichier invalide « " + valeur + " » (minuscules, chiffres, tirets ; .jpg, .png, .webp, .mp4)");
        }
    }

    private void cle(String f, String ici, String cle, Set<String> cles) {
        if (vide(cle)) {
            manquant(f, ici + ".cle");
        } else if (!CLE.matcher(cle).matches()) {
            erreur(f, ici + ".cle", "« " + cle + " » invalide (minuscules, chiffres et tirets)");
        } else if (!cles.add(cle)) {
            erreur(f, ici + ".cle", "« " + cle + " » en double");
        }
    }

    private void requis(String f, String champ, String valeur, int max) {
        if (vide(valeur)) {
            manquant(f, champ);
        } else if (valeur.length() > max) {
            erreur(f, champ, max + " caracteres au maximum");
        }
    }

    private void manquant(String f, String champ) {
        erreurs.add(f + " › " + champ + " : champ manquant");
    }

    private void erreur(String f, String champ, String message) {
        erreurs.add(f + " › " + champ + " : " + message);
    }

    private static String texte(JsonNode n, String champ) {
        JsonNode v = n.get(champ);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }

    private static List<String> trie(Set<String> s) {
        return s.stream().sorted().toList();
    }

    /** "lundi" -> 1 ... "dimanche" -> 7. */
    public static int numeroJour(String jour) {
        return JOURS.indexOf(jour) + 1;
    }
}
