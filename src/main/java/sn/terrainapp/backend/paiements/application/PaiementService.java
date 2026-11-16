package sn.terrainapp.backend.paiements.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sn.terrainapp.backend.paiements.application.PaymentProvider.DemandePaiement;
import sn.terrainapp.backend.paiements.application.PaymentProvider.ResultatInitiation;
import sn.terrainapp.backend.paiements.domaine.Paiement;
import sn.terrainapp.backend.paiements.domaine.StatutPaiement;
import sn.terrainapp.backend.paiements.infrastructure.PaiementRepository;
import sn.terrainapp.backend.reservations.domaine.Reservation;

/**
 * Paiement d'une reservation, dans la transaction de la reservation.
 *
 * En ligne : la reservation est creee EN_ATTENTE_PAIEMENT (le creneau est
 * tenu), le paiement est initie chez le prestataire, et seule sa
 * confirmation (immediate pour le simulateur, par webhook sinon) la
 * confirme. Paiement refuse = creneau libere.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PaiementService {

    private final PaymentProvider prestataire;
    private final PaiementRepository paiements;

    public PaiementService(PaymentProvider prestataire, PaiementRepository paiements) {
        this.prestataire = prestataire;
        this.paiements = paiements;
    }

    public record IssuePaiement(String reference, String redirectUrl, boolean simule) {
    }

    public IssuePaiement payerEnLigne(Reservation reservation) {
        ResultatInitiation r = prestataire.initier(new DemandePaiement(reservation.getId(), reservation.getMontant(),
                reservation.getMoyenPaiement(), reservation.getClientTelephone()));
        Paiement p = new Paiement(reservation.getId(), prestataire.code(), r.reference(), reservation.getMontant(),
                prestataire.estSimule());
        if (r.statutImmediat() != null) {
            p.definirStatut(r.statutImmediat());
            if (r.statutImmediat() == StatutPaiement.PAYE) {
                reservation.confirmerPaiement();
            } else if (r.statutImmediat() == StatutPaiement.ECHOUE) {
                reservation.expirer();
            }
        }
        paiements.save(p);
        return new IssuePaiement(r.reference(), r.redirectUrl(), prestataire.estSimule());
    }

    /** Comptoir : encaisse par l'accueil (especes ou paiement mobile verifie sur place). */
    public void encaisserAuComptoir(Reservation reservation) {
        Paiement p = new Paiement(reservation.getId(), "comptoir", "COMPTOIR-" + reservation.getReference(),
                reservation.getMontant(), false);
        p.definirStatut(StatutPaiement.PAYE);
        paiements.save(p);
    }
}
