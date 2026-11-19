import { test } from 'node:test'
import assert from 'node:assert/strict'
import { rmSync } from 'node:fs'
import path from 'node:path'
import { cheminLisible, valider } from '../src/validation.js'
import { copieExemple, EXEMPLE, modifier } from './outils.js'

const avec = async (fichier, modification) => {
  const d = modifier(copieExemple(), fichier, modification)
  return (await valider(d)).erreurs
}
const contient = (erreurs, attendu) =>
  assert.ok(erreurs.some((e) => e.includes(attendu)), `attendu « ${attendu} » dans :\n${erreurs.join('\n')}`)

test('clients/exemple livre est valide, sans avertissement', async () => {
  const r = await valider(EXEMPLE, { maintenant: new Date('2026-11-19T00:00:00Z') })
  assert.deepEqual(r.erreurs, [])
  assert.deepEqual(r.avertissements, [])
  assert.equal(r.donnees.terrains.length, 3)
})

test('chemins lisibles', () => {
  assert.equal(cheminLisible('/marque/couleurs/principale'), 'marque.couleurs.principale')
  assert.equal(cheminLisible('/0/plages/1/debut'), '[0].plages[1].debut')
})

test('champ manquant', async () => {
  contient(await avec('complexe.json', (c) => { delete c.marque.nom }), 'complexe.json › marque.nom : champ manquant')
})

test('couleur invalide', async () => {
  contient(await avec('complexe.json', (c) => { c.marque.couleurs.principale = 'vert' }),
    'complexe.json › marque.couleurs.principale : couleur invalide (format #RRGGBB, ex. #1F7A4D) (valeur : « vert »)')
})

test('logo absent du dossier assets', async () => {
  const d = copieExemple()
  rmSync(path.join(d, 'assets/logo.svg'))
  contient((await valider(d)).erreurs, 'assets/logo.svg : fichier absent (logo)')
})

test('logo non carre', async () => {
  contient(await avec('complexe.json', (c) => { c.marque.logo = 'accueil.jpg' }), 'assets/accueil.jpg : logo carré attendu (1600 × 900 px)')
})

test('tarif negatif', async () => {
  contient(await avec('donnees/terrains.json', (t) => { t[0].tarifHeure = -5000 }),
    'donnees/terrains.json › [0].tarifHeure : montant invalide : nombre entier de F CFA, positif ou nul (valeur : « -5000 »)')
})

test('durees incompatibles avec le pas des creneaux', async () => {
  contient(await avec('complexe.json', (c) => { c.reservation.pasCreneauMinutes = 60 }),
    'donnees/terrains.json › [0] (Terrain A).dureesProposees : durée 90 min incompatible avec le pas des créneaux (60 min)')
})

test('duree hors de 30 min a 3 h', async () => {
  contient(await avec('donnees/terrains.json', (t) => { t[0].dureesProposees = [60, 240] }),
    'donnees/terrains.json › [0].dureesProposees[1] : durée invalide (30, 60, 90, 120, 150 ou 180 minutes')
})

test('position GPS invalide', async () => {
  contient(await avec('complexe.json', (c) => { c.contact.gps.longitude = 200 }),
    'complexe.json › contact.gps.longitude : position GPS invalide : longitude de -180 à 180')
  contient(await avec('complexe.json', (c) => { c.contact.gps = { latitude: 0, longitude: 0 } }),
    'complexe.json › contact.gps : position 0,0')
})

test('horaires : jour mal ecrit et heure invalide', async () => {
  const e = await avec('complexe.json', (c) => { c.horaires.lundi = 'fermé'; c.horaires.mardi = { ouverture: '8h', fermeture: '23:00' } })
  contient(e, 'complexe.json › horaires.lundi : attendu "ferme" ou')
  contient(e, 'complexe.json › horaires.mardi.ouverture : heure invalide (format HH:mm')
})

test('references entre fichiers et cles en double', async () => {
  const e = await avec('donnees/terrains.json', (t) => { t[1].sport = 'basket'; t[2].cle = 'terrain-a' })
  contient(e, 'donnees/terrains.json › [1] (Grand terrain).sport : sport « basket » absent de donnees/sports.json')
  contient(e, 'donnees/terrains.json › [2].cle : « terrain-a » en double')
})

test('champ inconnu (faute de frappe)', async () => {
  contient(await avec('complexe.json', (c) => { c.marque.coleurs = c.marque.couleurs }), 'complexe.json › marque.coleurs : champ inconnu')
})

test('jamais de mot de passe dans le dossier', async () => {
  contient(await avec('donnees/personnel.json', (p) => { p[0].motDePasse = 'secret' }),
    'donnees/personnel.json › [0].motDePasse : aucun mot de passe dans le dossier')
})

test('photo de terrain manquante', async () => {
  contient(await avec('donnees/terrains.json', (t) => { t[0].photos.push('inconnue.jpg') }),
    'assets/terrains/inconnue.jpg : fichier absent (photo du terrain « Terrain A »)')
})

test('JSON illisible', async () => {
  const d = copieExemple()
  const { writeFileSync } = await import('node:fs')
  writeFileSync(path.join(d, 'donnees/sports.json'), '[{ "cle": "foot" ')
  contient((await valider(d)).erreurs, 'donnees/sports.json : JSON illisible')
})
