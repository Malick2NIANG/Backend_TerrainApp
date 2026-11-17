// Course au creneau : 50 joueurs envoient en meme temps une reservation sur
// LE MEME creneau. Exactement une doit reussir (201), les 49 autres doivent
// recevoir 409 CRENEAU_PRIS : jamais de double reservation.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {
  BASE_URL, annoncerCible, codeErreur, creneauLibre, jourDans, lireDispos, reserver, terrainsReservables,
} from './lib/commun.js';

const JOUEURS = Number(__ENV.JOUEURS || 50);
const gagnes = new Counter('course_gagnee');
const perdus = new Counter('course_perdue_409');

export const options = {
  scenarios: {
    course: { executor: 'per-vu-iterations', vus: JOUEURS, iterations: 1, maxDuration: '30s' },
  },
  thresholds: {
    course_gagnee: ['count==1'],
    course_perdue_409: [`count==${JOUEURS - 1}`],
    checks: ['rate==1'],
  },
};

export function setup() {
  annoncerCible('course-creneau');
  // Un creneau libre loin dans le calendrier (aucun autre scenario n'y touche).
  for (const terrainId of terrainsReservables()) {
    for (let j = 75; j < 90; j += 1) {
      const c = creneauLibre(lireDispos(jourDans(j), terrainId));
      if (c) return { terrainId, debut: c.debut, jour: jourDans(j) };
    }
  }
  throw new Error('Aucun creneau libre entre J+75 et J+90.');
}

export default function ({ terrainId, debut }) {
  const r = reserver(terrainId, debut);
  if (r.status === 201) gagnes.add(1);
  else if (r.status === 409 && codeErreur(r) === 'CRENEAU_PRIS') perdus.add(1);
  check(r, { 'reponse : 201 ou 409 CRENEAU_PRIS': (x) => x.status === 201 || codeErreur(x) === 'CRENEAU_PRIS' });
}

export function teardown({ terrainId, debut, jour }) {
  const r = http.get(`${BASE_URL}/disponibilites?date=${jour}&terrainId=${terrainId}`);
  const c = r.json()[0].creneaux.find((x) => x.debut === debut);
  console.log(`[course-creneau] etat final du creneau ${debut} : ${c ? c.etat : 'introuvable'}`);
}
