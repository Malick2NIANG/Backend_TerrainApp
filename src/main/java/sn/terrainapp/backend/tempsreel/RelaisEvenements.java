package sn.terrainapp.backend.tempsreel;

import java.time.Clock;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import sn.terrainapp.backend.reservations.infrastructure.ReservationRepository;

/** Publie l'outbox vers le planning du personnel, toutes les secondes, puis marque publie. */
@Component
public class RelaisEvenements {

    private final EvenementSortantRepository evenements;
    private final ReservationRepository reservations;
    private final FluxPlanning flux;
    private final Clock horloge;

    public RelaisEvenements(EvenementSortantRepository evenements, ReservationRepository reservations, FluxPlanning flux,
                            Clock horloge) {
        this.evenements = evenements;
        this.reservations = reservations;
        this.flux = flux;
        this.horloge = horloge;
    }

    @Scheduled(fixedDelay = 1_000)
    @Transactional
    public void relayer() {
        for (EvenementSortant e : evenements.aPublier()) {
            reservations.findById(e.getReservationId()).ifPresent(r -> flux.diffuser(e.getId(), e.getType(), r));
            e.marquerPublie(Instant.now(horloge));
        }
    }
}
