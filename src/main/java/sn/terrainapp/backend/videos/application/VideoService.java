package sn.terrainapp.backend.videos.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import sn.terrainapp.backend.commun.audit.JournalActions;
import sn.terrainapp.backend.commun.audit.JournalActions.Domaine;
import sn.terrainapp.backend.commun.erreurs.RequeteInvalideException;

/**
 * Videos des annonces : MP4 ou WebM, 30 Mo au plus. Le format est reconnu
 * a la signature du fichier (jamais a son nom ni au type annonce par le
 * navigateur). Nom aleatoire genere ici, stockage sur disque (volume Docker).
 * Pas de reencodage : le gerant depose une video courte, deja compressee.
 */
@Service
public class VideoService {

    public static final String CHEMIN_PUBLIC = "/api/v1/videos/";
    public static final long TAILLE_MAX_OCTETS = 30L * 1024 * 1024;
    private static final Pattern NOM_VALIDE =
            Pattern.compile("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(mp4|webm)$");

    public record VideoDeposee(String url, long octets, String type) {
    }

    public record VideoStockee(Path fichier, String typeMime) {
    }

    private final Path dossier;
    private final JournalActions journal;

    public VideoService(@Value("${app.videos.dossier}") String dossier, JournalActions journal) {
        this.dossier = Path.of(dossier).toAbsolutePath().normalize();
        this.journal = journal;
    }

    public VideoDeposee deposer(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new RequeteInvalideException("VIDEO_VIDE", "Aucune vidéo reçue.", "fichier");
        }
        if (fichier.getSize() > TAILLE_MAX_OCTETS) {
            throw new RequeteInvalideException("VIDEO_TROP_LOURDE", "Vidéo trop lourde : 30 Mo maximum.", "fichier");
        }
        String extension;
        try (InputStream entree = fichier.getInputStream()) {
            extension = extension(entree.readNBytes(12));
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture de la vidéo impossible", e);
        }
        if (extension == null) {
            throw new RequeteInvalideException("VIDEO_FORMAT", "Format non reconnu : déposez une vidéo MP4 ou WebM.", "fichier");
        }
        String nom = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(dossier);
            // Ecriture atomique : jamais de fichier a moitie ecrit servi a un client.
            Path temporaire = Files.createTempFile(dossier, "televersement-", ".tmp");
            try (InputStream entree = fichier.getInputStream()) {
                Files.copy(entree, temporaire, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporaire, dossier.resolve(nom), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Enregistrement de la vidéo impossible", e);
        }
        journal.enregistrer(Domaine.ANNONCE, "VIDEO", nom, "a déposé une vidéo");
        return new VideoDeposee(CHEMIN_PUBLIC + nom, fichier.getSize(), "mp4".equals(extension) ? "video/mp4" : "video/webm");
    }

    public Optional<VideoStockee> lire(String nom) {
        if (nom == null || !NOM_VALIDE.matcher(nom).matches()) {
            return Optional.empty();
        }
        Path fichier = dossier.resolve(nom).normalize();
        if (!fichier.startsWith(dossier) || !Files.isRegularFile(fichier)) {
            return Optional.empty();
        }
        return Optional.of(new VideoStockee(fichier, nom.endsWith(".mp4") ? "video/mp4" : "video/webm"));
    }

    /** "mp4" (boite ftyp ISO), "webm" (en-tete EBML), sinon null. */
    static String extension(byte[] debut) {
        if (debut.length >= 8 && debut[4] == 'f' && debut[5] == 't' && debut[6] == 'y' && debut[7] == 'p') {
            return "mp4";
        }
        if (debut.length >= 4 && (debut[0] & 0xFF) == 0x1A && (debut[1] & 0xFF) == 0x45
                && (debut[2] & 0xFF) == 0xDF && (debut[3] & 0xFF) == 0xA3) {
            return "webm";
        }
        return null;
    }
}
