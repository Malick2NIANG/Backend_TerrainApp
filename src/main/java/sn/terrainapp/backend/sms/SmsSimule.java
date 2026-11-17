package sn.terrainapp.backend.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SMS SIMULE (developpement / demo) : le message est ecrit dans le journal
 * du serveur au lieu d'etre envoye. Interdit en production
 * (VerificationConfigurationProd).
 */
@Component
@ConditionalOnProperty(name = "app.sms.prestataire", havingValue = "simule", matchIfMissing = true)
public class SmsSimule implements EnvoiSms {

    private static final Logger LOG = LoggerFactory.getLogger(SmsSimule.class);

    @Override
    public void envoyer(String telephone, String message) {
        String masque = telephone.length() > 4 ? "*".repeat(telephone.length() - 4) + telephone.substring(telephone.length() - 4) : telephone;
        LOG.warn("[SMS SIMULE] a {} : {}", masque, message);
    }
}
