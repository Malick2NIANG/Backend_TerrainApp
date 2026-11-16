package sn.terrainapp.backend.reservations.application;

import java.util.Optional;

import org.springframework.stereotype.Service;

import sn.terrainapp.backend.commun.erreurs.RequeteInvalideException;
import sn.terrainapp.backend.commun.idempotence.Idempotence;
import sn.terrainapp.backend.commun.securite.UtilisateurCourant;
import sn.terrainapp.backend.reservations.domaine.Canal;
import tools.jackson.databind.ObjectMapper;

/**
 * Point d'entree de la creation : en-tete Idempotency-Key obligatoire.
 * Meme cle et meme requete : la premiere reponse est rejouee (aucune seconde
 * reservation, aucun second paiement). Echec : la cle est liberee.
 */
@Service
public class CreationReservation {

    private final Idempotence idempotence;
    private final CreationReservationTransaction transaction;
    private final ObjectMapper json;

    public CreationReservation(Idempotence idempotence, CreationReservationTransaction transaction, ObjectMapper json) {
        this.idempotence = idempotence;
        this.transaction = transaction;
        this.json = json;
    }

    public record Resultat(String corpsJson, boolean rejouee) {
    }

    public Resultat creer(SaisieReservation saisie, Canal canal, String cleIdempotence) {
        if (cleIdempotence == null || cleIdempotence.isBlank()) {
            throw new RequeteInvalideException("IDEMPOTENCE_CLE_REQUISE",
                    "En-tete Idempotency-Key obligatoire.", "Idempotency-Key");
        }
        String cle = cleIdempotence.trim();
        String empreinte = Idempotence.empreinte(json.writeValueAsString(saisie) + "|" + canal + "|"
                + UtilisateurCourant.identifiant().orElse("client"));
        Optional<String> dejaTraitee = idempotence.reserver(cle, empreinte);
        if (dejaTraitee.isPresent()) {
            return new Resultat(dejaTraitee.get(), true);
        }
        try {
            return new Resultat(transaction.executer(saisie, canal, cle), false);
        } catch (RuntimeException echec) {
            idempotence.liberer(cle);
            throw echec;
        }
    }
}
