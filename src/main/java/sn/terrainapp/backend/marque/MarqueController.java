package sn.terrainapp.backend.marque;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import sn.terrainapp.backend.commun.web.CacheHttp;

@RestController
@EnableConfigurationProperties(Marque.class)
@Tag(name = "Complexe")
public class MarqueController {

    private final Marque marque;

    public MarqueController(Marque marque) {
        this.marque = marque;
    }

    @GetMapping("/marque")
    @Operation(summary = "Identite du complexe : nom, slogan, adresse, contacts")
    public ResponseEntity<Marque> marque() {
        return CacheHttp.lecture(marque);
    }
}
