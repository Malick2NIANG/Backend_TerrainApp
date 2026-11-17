// Soir de match : des joueurs reservent (5 -> 30 -> 60 en parallele)
// pendant que le gerant suit son planning. Chaque joueur lit les
// creneaux d'un jour, en prend un libre, paie (simule), puis ouvre son
// suivi. Un 409 CRENEAU_PRIS (creneau pris entre la lecture et l'envoi)
// est une reponse attendue sous concurrence : compte a part.
import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  PALIER, SEUILS_COMMUNS, SEUIL_DISPOS, SEUIL_RESERVATION, annoncerCible, codeErreur, connexionGerant,
  creneauLibre, creneauxDejaPris, dureeParcours, entierEntre, jourDans, lireDispos, reservationsCreees,
  reserver, suivre, terrainsReservables, BASE_URL,
} from './lib/commun.js';

const gerant = Boolean(__ENV.K6_MDP_GERANT);

export const options = {
  scenarios: Object.assign({
    joueurs: {
      executor: 'ramping-vus',
      exec: 'joueur',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 5 }, { duration: PALIER, target: 5 },
        { duration: '15s', target: 30 }, { duration: PALIER, target: 30 },
        { duration: '15s', target: 60 }, { duration: PALIER, target: 60 },
      ],
      gracefulRampDown: '10s',
    },
  }, gerant ? {
    gerant: { executor: 'constant-vus', exec: 'planningGerant', vus: 2, duration: '3m45s' },
  } : {}),
  thresholds: Object.assign({}, SEUILS_COMMUNS, SEUIL_DISPOS, SEUIL_RESERVATION,
    gerant ? { 'http_req_duration{groupe:planning}': ['p(95)<500'] } : {}),
};

export function setup() {
  annoncerCible('reservations');
  if (!gerant) console.warn('K6_MDP_GERANT absent : scenario gerant (planning) ignore.');
  return { terrains: terrainsReservables(), jeton: connexionGerant() };
}

export function joueur({ terrains }) {
  const debutParcours = Date.now();
  const terrainId = terrains[entierEntre(0, terrains.length - 1)];
  const creneau = creneauLibre(lireDispos(jourDans(entierEntre(1, 49)), terrainId));
  sleep(1 + Math.random() * 2); // le joueur choisit son creneau
  if (!creneau) return;
  const r = reserver(terrainId, creneau.debut);
  if (r.status === 409) {
    check(r, { 'conflit : creneau pris (409 CRENEAU_PRIS)': (x) => ['CRENEAU_PRIS', 'CRENEAU_PASSE'].includes(codeErreur(x)) });
    creneauxDejaPris.add(1);
    return;
  }
  if (check(r, {
    'reservation : 201': (x) => x.status === 201,
    'reservation : confirmee avec ticket': (x) => x.json('reservation.statut') === 'CONFIRMEE' && Boolean(x.json('reservation.codeTicket')),
  })) {
    reservationsCreees.add(1);
    sleep(1);
    suivre(r.json('reservation.id'), r.json('jetonSuivi'));
    dureeParcours.add(Date.now() - debutParcours);
  }
  sleep(1 + Math.random() * 2);
}

export function planningGerant({ jeton }) {
  const r = http.get(`${BASE_URL}/planning?date=${jourDans(entierEntre(1, 7))}`, {
    headers: { Authorization: `Bearer ${jeton}` }, tags: { groupe: 'planning', name: 'GET /planning' },
  });
  check(r, { 'planning gerant : 200': (x) => x.status === 200 });
  sleep(2);
}
