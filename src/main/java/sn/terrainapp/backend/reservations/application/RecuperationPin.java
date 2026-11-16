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
import sn.terrainapp.backend.marque.Marque;
import sn.terrainapp.backend.reservations.application.SuiviReservations.ReservationRetrouvee;
import sn.terrainapp.backend.reservations.domaine.Canal;
import sn.terrainapp.backend.reservations.domaine.CodeRecuperation;
import sn.terrainapp.backend.reservations.domaine.Reservation;
import sn.terrainapp.backend.reservations.infrastructure.CodeRecuperationRepository;
import sn.terrainapp.backend.reservations.infrastructure.ReservationRepository;
import sn.terrainapp.backend.sms.EnvoiSms;

/**
 * PIN oublie : un code de recuperation a 6 chiffres est envoye par SMS au
 * numero de la reservation (10 min, 5 essais, 3 envois par heure). Le bon
 * code permet de choisir un nouveau PIN, applique a toutes les reservations
 * en ligne de ce numero. La reponse a la demande est la meme que le numero
 * ait des reservations ou non (on ne revele pas qui reserve).
 */
@Service
public class RecuperationPin {

    private static final SecureRandom ALEA = new SecureRandom();

    private final CodeRecuperationRepository codes;
    private final ReservationRepository reservations;
    private final SuiviReservations suivi;
    private final EnvoiSms sms;
    private final Marque marque;
    private final Clock horloge;

    public RecuperationPin(CodeRecuperationRepository codes, ReservationRepository reservations, SuiviReservations suivi,
                           EnvoiSms sms, Marque marque, Clock horloge) {
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
        if (!c.renouveler(CodesPin.empreinteCode(code), maintenant)) {
            throw new TropDeTentativesException("Trop de codes demandés pour ce numéro. Réessayez dans une heure.");
        }
        codes.save(c);
        if (!reservations.findByClientTelephoneAndCanal(telephone, Canal.APP).isEmpty()) {
            sms.envoyer(telephone, marque.nom() + " : votre code de récupération est " + code
                    + " (valable 10 min). Ne le communiquez à personne.");
        }
    }

    @Transactional
    public List<ReservationRetrouvee> verifier(String saisieTelephone, String code, String nouveauPin) {
        String telephone = telephone(saisieTelephone);
        if (nouveauPin == null || !nouveauPin.matches("\\d{4}")) {
            throw new RequeteInvalideException("PIN_INVALIDE", "Le nouveau code PIN fait 4 chiffres.", "nouveauPin");
        }
        Instant maintenant = Instant.now(horloge);
        CodeRecuperation c = codes.verrouiller(telephone)
                .filter(x -> x.utilisable(maintenant))
                .orElseThrow(() -> new RequeteInvalideException("CODE_EXPIRE",
                        "Code expiré ou trop d'essais : demandez un nouveau code.", "code"));
        if (code == null || !CodesPin.correspondCode(code.trim(), c.getEmpreinte())) {
            c.essaiRate();
            throw new RequeteInvalideException("CODE_INCORRECT", "Code incorrect. Vérifiez le SMS reçu.", "code");
        }
        codes.delete(c);
        String empreinte = CodesPin.empreinte(nouveauPin);
        for (Reservation r : reservations.findByClientTelephoneAndCanal(telephone, Canal.APP)) {
            r.definirPin(empreinte);
        }
        reservations.flush();
        return suivi.retrouver(telephone, nouveauPin);
    }

    private static String telephone(String saisie) {
        return Telephone.normaliser(saisie).orElseThrow(() -> new RequeteInvalideException(
                "TELEPHONE_REQUIS", "Indiquez un numéro sénégalais à 9 chiffres.", "telephone"));
    }
}
