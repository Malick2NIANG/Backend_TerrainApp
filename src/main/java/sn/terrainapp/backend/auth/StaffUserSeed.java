package sn.terrainapp.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import sn.terrainapp.backend.security.Role;

@Component
@ConditionalOnProperty(name = "app.auth.seed.enabled", havingValue = "true")
public class StaffUserSeed implements CommandLineRunner {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.seed.gerant.password}")
    private String gerantPassword;

    @Value("${app.auth.seed.accueil.password}")
    private String accueilPassword;

    @Value("${app.auth.seed.controleur.password}")
    private String controleurPassword;

    @Value("${app.auth.seed.admin.password}")
    private String adminPassword;

    public StaffUserSeed(StaffUserRepository staffUserRepository, PasswordEncoder passwordEncoder) {
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        createIfMissing("gerant", gerantPassword, Role.GERANT);
        createIfMissing("accueil", accueilPassword, Role.ACCUEIL);
        createIfMissing("controleur", controleurPassword, Role.CONTROLEUR);
        createIfMissing("admin", adminPassword, Role.ADMIN);
    }

    private void createIfMissing(String username, String password, Role role) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Missing password for seeded user " + username);
        }
        if (staffUserRepository.findByUsernameIgnoreCase(username).isEmpty()) {
            staffUserRepository.save(new StaffUser(username, passwordEncoder.encode(password), role));
        }
    }
}