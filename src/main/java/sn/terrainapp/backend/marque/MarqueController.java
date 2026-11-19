package sn.terrainapp.backend.marque;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import sn.terrainapp.backend.commun.web.CacheHttp;
import sn.terrainapp.backend.complexe.IdentiteComplexe;

@RestController
@EnableConfigurationProperties(Marque.class)
@Tag(name = "Complexe")
public class MarqueController {

    private final IdentiteComplexe identite;

    public MarqueController(IdentiteComplexe identite) {
        this.identite = identite;
    }

    @GetMapping("/marque")
    @Operation(summary = "Identite du complexe : nom, slogan, contacts, couleurs, logo, position, "
            + "moyens de paiement, regles de reservation (dossier du complexe, sinon variables MARQUE_*)")
    public ResponseEntity<IdentiteComplexe.Vue> marque() {
        return CacheHttp.lecture(identite.vue());
    }
}
