package sn.terrainapp.backend.reservations.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import sn.terrainapp.backend.reservations.application.ControleTickets;
import sn.terrainapp.backend.reservations.application.ControleTickets.ResultatScan;
import sn.terrainapp.backend.reservations.application.CreationReservation;
import sn.terrainapp.backend.reservations.application.ReservationVue;
import sn.terrainapp.backend.reservations.application.SaisieReservation;
import sn.terrainapp.backend.reservations.application.RetrouverParSms;
import sn.terrainapp.backend.reservations.application.SuiviReservations;
import sn.terrainapp.backend.reservations.domaine.Canal;

@RestController
@Tag(name = "Reservations")
public class ReservationController {

    public static final String EN_TETE_JETON = "X-Jeton-Suivi";

    private final CreationReservation creation;
    private final SuiviReservations suivi;
    private final ControleTickets tickets;
    private final RetrouverParSms retrouver;

    public ReservationController(CreationReservation creation, SuiviReservations suivi, ControleTickets tickets,
                                 RetrouverParSms retrouver) {
        this.retrouver = retrouver;
        this.creation = creation;
        this.suivi = suivi;
        this.tickets = tickets;
    }

    @PostMapping(path = "/reservations", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reserver un creneau depuis l'App Client (paiement en ligne ; Idempotency-Key obligatoire)")
    public ResponseEntity<String> reserver(@Valid @RequestBody SaisieReservation saisie,
                                           @RequestHeader(name = "Idempotency-Key", required = false) String cle) {
        return reponse(creation.creer(saisie, Canal.APP, cle));
    }

    @PostMapping(path = "/reservations/comptoir", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ACCUEIL', 'GERANT')")
    @Operation(summary = "Reservation saisie et encaissee au comptoir (confirmee aussitot)")
    public ResponseEntity<String> comptoir(@Valid @RequestBody SaisieReservation saisie,
                                           @RequestHeader(name = "Idempotency-Key", required = false) String cle) {
        return reponse(creation.creer(saisie, Canal.COMPTOIR, cle));
    }

    private static ResponseEntity<String> reponse(CreationReservation.Resultat r) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .header("Idempotency-Replayed", String.valueOf(r.rejouee())).body(r.corpsJson());
    }

    @GetMapping("/reservations/{id}")
    @Operation(summary = "Detail d'une reservation : joueur avec son jeton (en-tete X-Jeton-Suivi), ou personnel")
    public ResponseEntity<ReservationVue> detail(@PathVariable long id,
                                                 @RequestHeader(name = EN_TETE_JETON, required = false) String jeton) {
        ReservationVue vue = sn.terrainapp.backend.commun.securite.UtilisateurCourant.role().isPresent()
                ? suivi.pourPersonnel(id) : suivi.pourClient(id, jeton);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vue);
    }

    public record DemandeCode(@NotBlank String telephone) {
    }

    public record DemandeRecherche(@NotBlank String telephone, @NotBlank String code) {
    }

    @PostMapping("/reservations/retrouver/code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Autre telephone : envoie un code par SMS au numero (meme reponse que le numero ait reserve ou non)")
    public void codeParSms(@Valid @RequestBody DemandeCode demande) {
        retrouver.demander(demande.telephone());
    }

    /** POST : le code ne doit jamais apparaitre dans une adresse (journaux, historique). */
    @PostMapping("/reservations/retrouver")
    @Operation(summary = "Retrouver ses reservations depuis un autre telephone : numero + code recu par SMS")
    public ResponseEntity<List<SuiviReservations.ReservationRetrouvee>> retrouver(@Valid @RequestBody DemandeRecherche demande) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(retrouver.verifier(demande.telephone(), demande.code()));
    }

    @GetMapping("/planning")
    @PreAuthorize("hasAnyRole('ACCUEIL', 'GERANT', 'CONTROLEUR')")
    @Operation(summary = "Reservations d'un jour (toutes, avec statut)")
    public List<ReservationVue> planning(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return suivi.planning(date);
    }

    @PostMapping("/reservations/{id}/annulation")
    @PreAuthorize("hasAnyRole('ACCUEIL', 'GERANT')")
    @Operation(summary = "Annuler une reservation (le creneau est libere)")
    public ReservationVue annuler(@PathVariable long id) {
        return suivi.annulerParPersonnel(id);
    }

    public record DemandeScan(@NotBlank String code) {
    }

    @PostMapping("/tickets/scan")
    @PreAuthorize("hasAnyRole('CONTROLEUR', 'ACCUEIL', 'GERANT')")
    @Operation(summary = "Scanner le QR d'un ticket a l'entree (verdict explique)")
    public ResultatScan scanner(@Valid @RequestBody DemandeScan demande) {
        return tickets.scanner(demande.code());
    }
}
