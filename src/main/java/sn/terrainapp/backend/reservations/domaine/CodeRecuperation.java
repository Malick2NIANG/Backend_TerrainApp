package sn.terrainapp.backend.reservations.domaine;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Code a usage unique envoye par SMS pour retrouver ses reservations : un a la fois par numero. */
@Entity
@Table(name = "codes_recuperation")
public class CodeRecuperation {

    public static final Duration VALIDITE = Duration.ofMinutes(10);
    public static final int MAX_ESSAIS = 5;
    public static final int MAX_ENVOIS_PAR_HEURE = 3;

    @Id
    @Column(length = 20)
    private String telephone;

    @Column(nullable = false, length = 100)
    private String empreinte;

    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    @Column(nullable = false)
    private int essais;

    @Column(nullable = false)
    private int envois;

    @Column(name = "premier_envoi_le", nullable = false)
    private Instant premierEnvoiLe;

    protected CodeRecuperation() {
    }

    public CodeRecuperation(String telephone) {
        this.telephone = telephone;
    }

    /** Nouveau code : le precedent ne vaut plus. @return faux si 3 envois dans l'heure. */
    public boolean renouveler(String empreinte, Instant maintenant) {
        if (premierEnvoiLe == null || premierEnvoiLe.plus(Duration.ofHours(1)).isBefore(maintenant)) {
            premierEnvoiLe = maintenant;
            envois = 0;
        }
        if (envois >= MAX_ENVOIS_PAR_HEURE) {
            return false;
        }
        envois++;
        this.empreinte = empreinte;
        this.expireLe = maintenant.plus(VALIDITE);
        this.essais = 0;
        return true;
    }

    public boolean utilisable(Instant maintenant) {
        return essais < MAX_ESSAIS && maintenant.isBefore(expireLe);
    }

    public void essaiRate() {
        essais++;
    }

    public String getTelephone() { return telephone; }
    public String getEmpreinte() { return empreinte; }
    public Instant getPremierEnvoiLe() { return premierEnvoiLe; }
}
