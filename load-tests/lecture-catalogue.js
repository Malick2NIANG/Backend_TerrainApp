// Ouverture de l'App Client en paliers : 10 -> 50 -> 200 -> 500 joueurs
// (PALIERS=10,50,200,500, PALIER=1m par defaut). Chaque joueur charge
// l'accueil, ouvre une fiche terrain et regarde les creneaux d'un jour.
import { sleep } from 'k6';
import {
  PALIER, SEUILS_COMMUNS, SEUIL_DISPOS, SEUIL_LECTURE, annoncerCible, entierEntre, jourDans,
  lireAccueil, lireDispos, lireTerrain,
} from './lib/commun.js';

const PALIERS = (__ENV.PALIERS || '10,50,200,500').split(',').map(Number);

export const options = {
  scenarios: {
    joueurs: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: PALIERS.flatMap((n) => [{ duration: '10s', target: n }, { duration: PALIER, target: n }]),
      gracefulRampDown: '10s',
    },
  },
  thresholds: Object.assign({}, SEUILS_COMMUNS, SEUIL_LECTURE, SEUIL_DISPOS),
};

export function setup() {
  annoncerCible('lecture-catalogue');
}

export default function () {
  const terrains = lireAccueil();
  sleep(1 + Math.random() * 2);
  if (terrains.length) {
    const t = terrains[entierEntre(0, terrains.length - 1)];
    lireTerrain(t.id);
    sleep(0.5 + Math.random());
    lireDispos(jourDans(entierEntre(0, 14)), t.id);
  }
  sleep(1 + Math.random() * 2);
}
