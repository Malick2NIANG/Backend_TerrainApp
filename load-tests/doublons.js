// Idempotence : la meme reservation envoyee 5 fois en parallele avec la
// meme Idempotency-Key (double appui, reseau qui rejoue) ne cree qu'une
// reservation. Les envois simultanes recoivent soit la meme reponse (201,
// meme id), soit 409 REQUETE_EN_COURS ; un nouvel essai ensuite rejoue la
// reponse d'origine. La meme cle avec un autre contenu est refusee
// (409 IDEMPOTENCE_CONFLIT).
import http from 'k6/http';
import { check } from 'k6';
import {
  BASE_URL, annoncerCible, cle, codeErreur, corpsReservation, creneauLibre, jourDans, lireDispos, terrainsReservables,
} from './lib/commun.js';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1'] },
};

export function setup() {
  annoncerCible('doublons');
  const creneaux = [];
  for (const terrainId of terrainsReservables()) {
    for (let j = 50; j < 55 && creneaux.length < 2; j += 1) {
      const libres = lireDispos(jourDans(j), terrainId).filter((c) => c.etat === 'LIBRE');
      for (const c of libres.slice(0, 2 - creneaux.length)) creneaux.push({ terrainId, debut: c.debut });
    }
    if (creneaux.length === 2) return { creneaux };
  }
  throw new Error('Pas assez de creneaux libres entre J+50 et J+54 (periode reservable : 60 jours).');
}

export default function ({ creneaux: [a, b] }) {
  const k = cle();
  const corps = corpsReservation(a.terrainId, a.debut);
  const params = {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': k },
    responseCallback: http.expectedStatuses(201, 409),
  };
  const reps = http.batch(Array.from({ length: 5 }, () => ['POST', `${BASE_URL}/reservations`, corps, params]));
  const ids = reps.filter((r) => r.status === 201).map((r) => r.json('reservation.id'));
  check(reps, {
    '5 envois : 201 (meme reponse) ou 409 REQUETE_EN_COURS': (r) => r.every((x) => x.status === 201
      || (x.status === 409 && codeErreur(x) === 'REQUETE_EN_COURS')),
    '5 envois : au moins un 201, un seul id de reservation': () => ids.length >= 1 && new Set(ids).size === 1,
  });
  // Le joueur reessaie (ou l'app rejoue) : meme reponse, aucune nouvelle reservation.
  const rejeu = http.post(`${BASE_URL}/reservations`, corps, params);
  check(rejeu, {
    'nouvel essai : 201 rejoue (Idempotency-Replayed: true)': (x) => x.status === 201 && x.headers['Idempotency-Replayed'] === 'true',
    'nouvel essai : meme reservation': (x) => x.status === 201 && x.json('reservation.id') === ids[0],
  });
  const autre = http.post(`${BASE_URL}/reservations`, corpsReservation(b.terrainId, b.debut), params);
  check(autre, { 'meme cle, autre contenu : 409 IDEMPOTENCE_CONFLIT': (x) => x.status === 409 && codeErreur(x) === 'IDEMPOTENCE_CONFLIT' });
}
