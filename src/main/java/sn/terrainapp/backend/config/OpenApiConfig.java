package sn.terrainapp.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI terrainAppOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TerrainApp API")
                        .description("API du backend TerrainApp — authentification, terrains, reservations, paiements.")
                        .version("v1"));
    }
}
