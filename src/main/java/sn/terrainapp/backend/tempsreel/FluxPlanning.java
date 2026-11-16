package sn.terrainapp.backend.tempsreel;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import sn.terrainapp.backend.reservations.application.ReservationVue;
import sn.terrainapp.backend.reservations.domaine.Reservation;

/**
 * Postes du personnel connectes au planning temps reel (SSE) : chaque
 * reservation creee, payee, annulee, expiree ou scannee y est poussee.
 * En memoire : une seule instance du backend par complexe.
 */
@Component
public class FluxPlanning implements SmartLifecycle {

    /** Charge d'un evenement : type + reservation (vue personnel). */
    public record Evenement(String type, ReservationVue reservation) {
    }

    private final List<SseEmitter> abonnes = new CopyOnWriteArrayList<>();
    private volatile boolean actif;

    @Override
    public void start() {
        actif = true;
    }

    /** Arret : flux fermes avant Tomcat, sinon chaque redemarrage attend 30 s. */
    @Override
    public void stop() {
        actif = false;
        for (SseEmitter e : abonnes) {
            try {
                e.complete();
            } catch (RuntimeException dejaFerme) {
                // rien a faire
            }
        }
        abonnes.clear();
    }

    @Override
    public boolean isRunning() {
        return actif;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    public void abonner(SseEmitter emetteur) {
        abonnes.add(emetteur);
        emetteur.onCompletion(() -> abonnes.remove(emetteur));
        emetteur.onTimeout(() -> abonnes.remove(emetteur));
        emetteur.onError(e -> abonnes.remove(emetteur));
    }

    public int nombreAbonnes() {
        return abonnes.size();
    }

    public void diffuser(long idEvenement, String type, Reservation r) {
        for (SseEmitter e : abonnes) {
            envoyer(e, idEvenement, type, r);
        }
    }

    public void envoyer(SseEmitter emetteur, long idEvenement, String type, Reservation r) {
        try {
            emetteur.send(SseEmitter.event().id(String.valueOf(idEvenement)).name("reservation")
                    .data(new Evenement(type, ReservationVue.pourPersonnel(r)), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException deconnecte) {
            abonnes.remove(emetteur);
        }
    }

    /** Signal de vie toutes les 25 s (proxys, postes deconnectes). */
    @Scheduled(fixedRate = 25_000)
    void signalDeVie() {
        for (SseEmitter e : abonnes) {
            try {
                e.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException deconnecte) {
                abonnes.remove(e);
            }
        }
    }
}
