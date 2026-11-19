package sn.terrainapp.backend.reservations.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.terrainapp.backend.commun.erreurs.RequeteInvalideException;
import sn.terrainapp.backend.commun.erreurs.TropDeTentativesException;
import sn.terrainapp.backend.commun.telephone.Telephone;
import sn.terrainapp.backend.complexe.IdentiteComplexe;
import sn.terrainapp.backend.reservations.application.SuiviReservations.ReservationRetrouvee;
import sn.terrainapp.backend.reservations.domaine.CodeRecuperation;
import sn.terrainapp.backend.reservations.infrastructure.CodeRecuperationRepository;
import sn.terrainapp.backend.reservations.infrastructure.ReservationRepository;
import sn.terrainapp.backend.sms.EnvoiSms;

/**
 * Retrouver ses reservations depuis un autre telephone : un code a 6
 * chiffres est envoye par SMS au numero (10 min, 5 essais, 3 envois par
 * heure). Le bon code rend les reservations de ce numero (en ligne et au
 * comptoir), chacune avec un jeton d'acces. La reponse a la demande est la
 * meme que le numero ait reserve ou non (on ne revele pas qui reserve).
 */
@Service
public class RetrouverParSms {

    private static final SecureRandom ALEA = new SecureRandom();

    private final CodeRecuperationRepository codes;
    private final ReservationRepository reservations;
    private final SuiviReservations suivi;
    private final EnvoiSms sms;
    private final IdentiteComplexe marque;
    private final Clock horloge;

    public RetrouverParSms(CodeRecuperationRepository codes, ReservationRepository reservations, SuiviReservations suivi,
                           EnvoiSms sms, IdentiteComplexe marque, Clock horloge) {
        this.codes = codes;
        this.reservations = reservations;
        this.suivi = suivi;
        this.sms = sms;
        this.marque = marque;
        this.horloge = horloge;
    }

    @Transactional
    public void demander(String saisieTelephone) {
        String telephone = telephone(saisieTelephone);
        Instant maintenant = Instant.now(horloge);
        String code = String.format("%06d", ALEA.nextInt(1_000_000));
        CodeRecuperation c = codes.verrouiller(telephone).orElseGet(() -> new CodeRecuperation(telephone));
        if (!c.renouveler(CodesSms.empreinte(code), maintenant)) {
            throw new TropDeTentativesException("Trop de codes demandés pour ce numéro. Réessayez dans une heure.");
        }
        codes.save(c);
        if (reservations.existsByClientTelephone(telephone)) {
            sms.envoyer(telephone, marque.nom() + " : votre code pour retrouver vos réservations est " + code
                    + " (valable 10 min). Ne le communiquez à personne.");
        }
    }

    @Transactional
    public List<ReservationRetrouvee> verifier(String saisieTelephone, String code) {
        String telephone = telephone(saisieTelephone);
        Instant maintenant = Instant.now(horloge);
        CodeRecuperation c = codes.verrouiller(telephone)
                .filter(x -> x.utilisable(maintenant))
                .orElseThrow(() -> new RequeteInvalideException("CODE_EXPIRE",
                        "Code expiré ou trop d'essais : demandez un nouveau code.", "code"));
        if (code == null || !CodesSms.correspond(code.trim(), c.getEmpreinte())) {
            c.essaiRate();
            throw new RequeteInvalideException("CODE_INCORRECT", "Code incorrect. Vérifiez le SMS reçu.", "code");
        }
        codes.delete(c);
        return suivi.duNumero(telephone);
    }

    private static String telephone(String saisie) {
        return Telephone.normaliser(saisie).orElseThrow(() -> new RequeteInvalideException(
                "TELEPHONE_REQUIS", "Indiquez un numéro sénégalais à 9 chiffres.", "telephone"));
    }
}
