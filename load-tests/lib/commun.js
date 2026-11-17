// Outils communs aux scenarios de charge TerrainApp.
//
// - BASE_URL et liste blanche d'hotes (refus de toute autre cible) ;
// - connexion du gerant (mot de passe UNIQUEMENT en variable d'environnement) ;
// - lecture du catalogue et des disponibilites, choix d'un creneau libre ;
// - reservation joueur (Idempotency-Key, paiement simule) et suivi.
//
// Aucun mot de passe, jeton ou secret n'est ecrit dans ces fichiers.

import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const HOTES_AUTORISES = ['localhost', '127.0.0.1', 'host.docker.internal'].concat(
  (__ENV.ALLOWED_HOSTS || '').split(',').map((h) => h.trim().toLowerCase()).filter(Boolean),
);

function hoteDe(url) {
  const m = /^https?:\/\/(\[[^\]]+\]|[^/:?#]+)(?::\d+)?(?:[/?#].*)?$/i.exec(url || '');
  if (!m) fail(`URL invalide : "${url}"`);
  return m[1].toLowerCase();
}

function cibleAutorisee(url) {
  const hote = hoteDe(url);
  if (!HOTES_AUTORISES.includes(hote)) {
    throw new Error(`BASE_URL vise "${hote}", hors liste blanche (${HOTES_AUTORISES.join(', ')}). `
      + 'Ajoutez-le a ALLOWED_HOSTS UNIQUEMENT pour un environnement de test, jamais la production.');
  }
  return url.replace(/\/+$/, '');
}

// Pile de charge jetable (load-tests/docker-compose.yml), port 8091.
export const BASE_URL = cibleAutorisee(__ENV.BASE_URL || 'http://host.docker.internal:8091/api/v1');
export const PALIER = __ENV.PALIER || '1m';

export function annoncerCible(nom) {
  console.log(`[${nom}] cible : ${hoteDe(BASE_URL)} (${BASE_URL})`);
}

// ---------------------------------------------------------------------------
// Seuils
// ---------------------------------------------------------------------------

export const SEUILS_COMMUNS = {
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
};
export const SEUIL_LECTURE = { 'http_req_duration{groupe:catalogue}': ['p(95)<300'] };
export const SEUIL_DISPOS = { 'http_req_duration{groupe:dispos}': ['p(95)<400'] };
export const SEUIL_RESERVATION = { 'http_req_duration{groupe:reservation}': ['p(95)<800'] };

// 409 (creneau pris, conflit d'idempotence) : reponse metier attendue sous
// concurrence, pas une panne.
const ATTENDUS_RESERVATION = http.expectedStatuses(201, 409);

export const reservationsCreees = new Counter('reservations_creees');
export const creneauxDejaPris = new Counter('creneaux_deja_pris');
export const dureeParcours = new Trend('duree_parcours_reservation', true);

// ---------------------------------------------------------------------------
// Dates (Dakar = UTC)
// ---------------------------------------------------------------------------

export function jourDans(n) {
  const d = new Date(Date.now() + n * 86400000);
  return d.toISOString().slice(0, 10);
}

export function entierEntre(min, max) {
  return min + Math.floor(Math.random() * (max - min + 1));
}

/** Telephone senegalais fictif, varie (77/78/76/70/75 + 7 chiffres). */
export function telephone() {
  const prefixe = ['77', '78', '76', '70', '75'][entierEntre(0, 4)];
  return `${prefixe}${String(entierEntre(0, 9999999)).padStart(7, '0')}`;
}

export function cle() {
  return `k6-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

// ---------------------------------------------------------------------------
// Lectures publiques (App Client)
// ---------------------------------------------------------------------------

/** Ouverture de l'App Client : marque, sports, terrains, horaires, tendances, annonces. */
export function lireAccueil() {
  const tag = { tags: { groupe: 'catalogue' } };
  const rep = http.batch([
    ['GET', `${BASE_URL}/marque`, null, tag],
    ['GET', `${BASE_URL}/sports`, null, tag],
    ['GET', `${BASE_URL}/terrains`, null, tag],
    ['GET', `${BASE_URL}/horaires`, null, tag],
    ['GET', `${BASE_URL}/terrains/tendances?nombre=4`, null, tag],
    ['GET', `${BASE_URL}/annonces`, null, tag],
  ]);
  check(rep, { 'accueil : 6 lectures en 200': (r) => r.every((x) => x.status === 200) });
  const terrains = rep[2].status === 200 ? rep[2].json() : [];
  return terrains;
}

export function lireTerrain(id) {
  const r = http.get(`${BASE_URL}/terrains/${id}`, { tags: { groupe: 'catalogue', name: 'GET /terrains/{id}' } });
  check(r, { 'fiche terrain : 200': (x) => x.status === 200 });
}

/** Creneaux d'un terrain sur un jour (jamais en cache). */
export function lireDispos(jour, terrainId) {
  const r = http.get(`${BASE_URL}/disponibilites?date=${jour}&terrainId=${terrainId}`,
    { tags: { groupe: 'dispos', name: 'GET /disponibilites' } });
  check(r, { 'disponibilites : 200': (x) => x.status === 200 });
  if (r.status !== 200) return [];
  const t = r.json()[0];
  return t ? t.creneaux : [];
}

export function creneauLibre(creneaux) {
  const libres = creneaux.filter((c) => c.etat === 'LIBRE');
  return libres.length ? libres[entierEntre(0, libres.length - 1)] : null;
}

// ---------------------------------------------------------------------------
// Reservation joueur (paiement simule immediat) et suivi
// ---------------------------------------------------------------------------

export function corpsReservation(terrainId, debut) {
  return JSON.stringify({
    terrainId, debut, clientNom: `Joueur k6 ${__VU}`, clientTelephone: telephone(), moyenPaiement: 'wave',
  });
}

export function reserver(terrainId, debut, cleIdem = cle(), corps = corpsReservation(terrainId, debut)) {
  return http.post(`${BASE_URL}/reservations`, corps, {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': cleIdem },
    tags: { groupe: 'reservation', name: 'POST /reservations' },
    responseCallback: ATTENDUS_RESERVATION,
  });
}

export function codeErreur(r) {
  try { return r.json('code'); } catch { return null; }
}

/** Suivi sans compte : fiche de la reservation avec le jeton recu. */
export function suivre(id, jeton) {
  const r = http.get(`${BASE_URL}/reservations/${id}`, {
    headers: { 'X-Jeton-Suivi': jeton }, tags: { groupe: 'suivi', name: 'GET /reservations/{id}' },
  });
  check(r, { 'suivi : 200 et confirmee': (x) => x.status === 200 && x.json('statut') === 'CONFIRMEE' });
  return r;
}

// ---------------------------------------------------------------------------
// Gerant
// ---------------------------------------------------------------------------

/** Jeton du gerant (K6_MDP_GERANT), ou null si le mot de passe n'est pas fourni. */
export function connexionGerant() {
  const motDePasse = __ENV.K6_MDP_GERANT;
  if (!motDePasse) return null;
  const r = http.post(`${BASE_URL}/auth/login`, JSON.stringify({ username: __ENV.K6_ID_GERANT || 'gerant', password: motDePasse }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /auth/login' } });
  if (r.status !== 200) fail(`Connexion gerant refusee (${r.status}).`);
  return r.json('token');
}

/** Terrains reservables (au moins un). */
export function terrainsReservables() {
  const r = http.get(`${BASE_URL}/terrains`);
  if (r.status !== 200) fail(`GET /terrains : ${r.status}. La pile de charge est-elle demarree ?`);
  const ids = r.json().map((t) => t.id);
  if (!ids.length) fail('Aucun terrain publie dans la pile de charge.');
  return ids;
}
