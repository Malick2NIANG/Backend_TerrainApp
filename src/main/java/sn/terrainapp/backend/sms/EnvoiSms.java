package sn.terrainapp.backend.sms;

/**
 * Port d'envoi de SMS. Implementation actuelle : SmsSimule (journal du
 * serveur). Le vrai prestataire (etape 4) implementera ce meme port.
 */
public interface EnvoiSms {

    void envoyer(String telephone, String message);
}
