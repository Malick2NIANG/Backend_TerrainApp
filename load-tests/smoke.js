// Fumee : 1 joueur, 30 s. Accueil, fiche, disponibilites, une reservation
// (paiement simule) et son suivi. Verifie que la pile repond avant les
// scenarios plus lourds.
import { check, sleep } from 'k6';
import {
  SEUILS_COMMUNS, annoncerCible, creneauLibre, jourDans, lireAccueil, lireDispos, lireTerrain,
  reservationsCreees, reserver, suivre, terrainsReservables, entierEntre,
} from './lib/commun.js';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: SEUILS_COMMUNS,
};

export function setup() {
  annoncerCible('smoke');
  return { terrains: terrainsReservables() };
}

export default function ({ terrains }) {
  lireAccueil();
  const terrainId = terrains[entierEntre(0, terrains.length - 1)];
  lireTerrain(terrainId);
  const creneau = creneauLibre(lireDispos(jourDans(entierEntre(1, 30)), terrainId));
  if (creneau) {
    const r = reserver(terrainId, creneau.debut);
    if (check(r, { 'reservation : 201': (x) => x.status === 201 })) {
      reservationsCreees.add(1);
      suivre(r.json('reservation.id'), r.json('jetonSuivi'));
    }
  }
  sleep(2);
}
