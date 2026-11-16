package sn.terrainapp.backend.videos.web;

import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import sn.terrainapp.backend.commun.erreurs.RessourceIntrouvableException;
import sn.terrainapp.backend.videos.application.VideoService;
import sn.terrainapp.backend.videos.application.VideoService.VideoDeposee;
import sn.terrainapp.backend.videos.application.VideoService.VideoStockee;

@RestController
@Tag(name = "Videos")
public class VideoController {

    private final VideoService videos;

    public VideoController(VideoService videos) {
        this.videos = videos;
    }

    @PostMapping(path = "/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Deposer une video d'annonce (MP4 ou WebM, 30 Mo max)")
    public VideoDeposee deposer(@RequestParam("fichier") MultipartFile fichier) {
        return videos.deposer(fichier);
    }

    /**
     * Lecture en flux : Spring sert les requetes partielles (Range), exigees
     * par Safari iOS pour lire une video. Nom unique : cache d'un an.
     */
    @GetMapping("/videos/{nom}")
    public ResponseEntity<Resource> lire(@PathVariable String nom) {
        VideoStockee video = videos.lire(nom).orElseThrow(() -> new RessourceIntrouvableException("Vidéo introuvable."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(video.typeMime()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(video.fichier()));
    }
}
