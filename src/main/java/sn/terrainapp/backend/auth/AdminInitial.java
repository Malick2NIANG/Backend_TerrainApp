package sn.terrainapp.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import sn.terrainapp.backend.commun.audit.JournalActions;
import sn.terrainapp.backend.security.Role;

/**
 * Premier compte ADMIN d'une base de production (ou aucun compte n'existe :
 * le seed de demonstration y est interdit et l'API refuse de creer un ADMIN).
 *
 * Usage unique : renseigner APP_AUTH_ADMIN_INITIAL_IDENTIFIANT et
 * APP_AUTH_ADMIN_INITIAL_MOT_DE_PASSE, demarrer, puis RETIRER les deux
 * variables. Le compte est cree avec un mot de passe temporaire a changer a la
 * premiere connexion. S'il existe deja un ADMIN, rien n'est fait.
 */
@Component
@Order(1)
public class AdminInitial implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminInitial.class);
    static final int LONGUEUR_MIN = 12;

    private final StaffUserRepository comptes;
    private final PasswordEncoder encodeur;
    private final JournalActions journal;
    private final String identifiant;
    private final String motDePasse;

    public AdminInitial(StaffUserRepository comptes, PasswordEncoder encodeur, JournalActions journal,
                          @Value("${app.auth.admin-initial.identifiant:}") String identifiant,
                          @Value("${app.auth.admin-initial.mot-de-passe:}") String motDePasse) {
        this.comptes = comptes;
        this.encodeur = encodeur;
        this.journal = journal;
        this.identifiant = identifiant == null ? "" : identifiant.trim();
        this.motDePasse = motDePasse == null ? "" : motDePasse;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean avecIdentifiant = !identifiant.isEmpty();
        boolean avecMotDePasse = !motDePasse.isBlank();
        if (!avecIdentifiant && !avecMotDePasse) {
            return;
        }
        if (avecIdentifiant != avecMotDePasse) {
            throw new IllegalStateException("APP_AUTH_ADMIN_INITIAL_IDENTIFIANT et "
                    + "APP_AUTH_ADMIN_INITIAL_MOT_DE_PASSE doivent etre renseignes ensemble");
        }
        if (motDePasse.length() < LONGUEUR_MIN) {
            throw new IllegalStateException("APP_AUTH_ADMIN_INITIAL_MOT_DE_PASSE : " + LONGUEUR_MIN
                    + " caracteres minimum");
        }
        if (comptes.existsByRole(Role.ADMIN)) {
            LOG.warn("Un compte ADMIN existe deja : APP_AUTH_ADMIN_INITIAL_* ignorees, retirez-les.");
            return;
        }
        if (comptes.findByUsernameIgnoreCase(identifiant).isPresent()) {
            throw new IllegalStateException("APP_AUTH_ADMIN_INITIAL_IDENTIFIANT : identifiant deja utilise");
        }
        StaffUser manager = comptes.save(new StaffUser(identifiant, encodeur.encode(motDePasse), Role.ADMIN, true));
        journal.enregistrerEnTantQue("systeme", JournalActions.Domaine.PERSONNEL, "CREATION",
                String.valueOf(manager.getId()), "a cree le premier compte Manager (mot de passe a changer a la premiere connexion)");
        LOG.info("Premier compte ADMIN cree ({}). Retirez maintenant APP_AUTH_ADMIN_INITIAL_*.", identifiant);
    }
}
