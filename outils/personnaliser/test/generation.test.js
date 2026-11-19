import { test } from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, mkdtempSync, readFileSync, readdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import bcrypt from 'bcryptjs'
import sharp from 'sharp'
import { generer, lireActivations, lireEnv, nouveauCode } from '../src/generation.js'
import { valider } from '../src/validation.js'
import { analyserArguments, principal, resoudreDossier } from '../src/cli.js'
import { copieExemple, EXEMPLE } from './outils.js'

const sortieTemp = () => path.join(mkdtempSync(path.join(tmpdir(), 'deploiement-')), 'exemple')

async function produire(options = {}, sortie = sortieTemp(), dossier = EXEMPLE) {
  const lu = await valider(dossier)
  const r = await generer(dossier, lu, sortie, options)
  return { ...r, sortie, env: lireEnv(path.join(sortie, '.env')) }
}

test('deploiement complet produit', async () => {
  const { sortie, env, nouveauxCodes } = await produire({ profil: 'demo' })
  for (const f of ['docker-compose.yml', '.env', '.gitignore', 'CODES-ACTIVATION.txt', 'complexe/complexe.json',
    'complexe/donnees/terrains.json', 'marque/marque.json', 'marque/logo.svg', 'marque/terrains/terrain-a-1.jpg',
    'marque/icons/icon-192.png', 'marque/icons/maskable-512.png', 'marque/icons/apple-touch-icon.png', 'marque/favicon.png']) {
    assert.ok(existsSync(path.join(sortie, f)), `${f} attendu`)
  }
  assert.equal(readFileSync(path.join(sortie, '.gitignore'), 'utf8').trim().split('\n').pop(), '*')
  assert.equal(nouveauxCodes.length, 3)
  assert.equal(env.MARQUE_NOM, 'Arena Exemple')
  assert.equal(env.MARQUE_COULEUR, '#0F766E')
  assert.equal(env.MARQUE_LOGO_URL, '/marque/logo.svg')
  assert.equal(env.SPRING_PROFILES_ACTIVE, 'dev')
  assert.equal(env.SPRING_FLYWAY_LOCATIONS, 'classpath:db/migration')
  assert.match(env.APP_CORS_ALLOWED_ORIGINS, /https:\/\/reserver\.arena-exemple\.sn/)
  assert.match(env.JWT_SECRET, /^[0-9a-f]{96}$/)
})

test('icones PWA aux bonnes dimensions', async () => {
  const { sortie } = await produire()
  for (const [nom, cote] of [['icon-192.png', 192], ['icon-512.png', 512], ['maskable-512.png', 512], ['apple-touch-icon.png', 180]]) {
    const m = await sharp(path.join(sortie, 'marque/icons', nom)).metadata()
    assert.deepEqual([m.width, m.height], [cote, cote], nom)
  }
})

test('identite publique : memes champs que GET /marque du backend', async () => {
  const { sortie } = await produire()
  const m = JSON.parse(readFileSync(path.join(sortie, 'marque/marque.json'), 'utf8'))
  assert.equal(m.nom, 'Arena Exemple')
  assert.equal(m.personnalise, true)
  assert.deepEqual(m.couleurs, { principale: '#0F766E', foncee: '#0B4F4A', claire: '#5EEAD4' })
  assert.equal(m.logoUrl, '/marque/logo.svg')
  assert.equal(m.imageAccueilUrl, '/marque/accueil.jpg')
  assert.deepEqual(m.moyensPaiement, ['wave', 'orange-money', 'mixx-yas'])
  assert.deepEqual(m.regles, { pasCreneauMinutes: 30, delaiAnnulationHeures: 24, margeEntreeMinutes: 30 })
  assert.equal(m.documents.nomSurTickets, 'ARENA EXEMPLE')
  assert.deepEqual(m.gps, { latitude: 14.791, longitude: -16.9256 })
})

test('codes d activation : empreinte BCrypt seulement, code jamais dans le .env', async () => {
  const { env, nouveauxCodes, sortie } = await produire()
  const empreintes = lireActivations(env.APP_COMPLEXE_ACTIVATIONS)
  assert.deepEqual(Object.keys(empreintes).sort(), ['accueil', 'controleur', 'gerant'])
  for (const { identifiant, code } of nouveauxCodes) {
    assert.match(code, /^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$/)
    assert.ok(bcrypt.compareSync(code, empreintes[identifiant]))
    assert.ok(!readFileSync(path.join(sortie, '.env'), 'utf8').includes(code))
    assert.ok(!readFileSync(path.join(sortie, 'docker-compose.yml'), 'utf8').includes(code))
  }
})

test('rejouable : secrets et codes conserves, marque regeneree', async () => {
  const sortie = sortieTemp()
  const premier = await produire({}, sortie)
  const second = await produire({}, sortie)
  assert.equal(second.nouveauxCodes.length, 0, 'aucun nouveau code au 2e passage')
  for (const cle of ['JWT_SECRET', 'MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'APP_COMPLEXE_ACTIVATIONS']) {
    assert.equal(second.env[cle], premier.env[cle], cle)
  }
})

test('nouveau compte ajoute plus tard : seul lui recoit un code', async () => {
  const sortie = sortieTemp()
  const dossier = copieExemple()
  const premier = await produire({}, sortie, dossier)
  const { modifier } = await import('./outils.js')
  modifier(dossier, 'donnees/personnel.json', (p) => { p.push({ identifiant: 'caisse2', role: 'ACCUEIL' }) })
  const second = await produire({}, sortie, dossier)
  assert.deepEqual(second.nouveauxCodes.map((n) => n.identifiant), ['caisse2'])
  const avant = lireActivations(premier.env.APP_COMPLEXE_ACTIVATIONS)
  const apres = lireActivations(second.env.APP_COMPLEXE_ACTIVATIONS)
  assert.equal(apres.gerant, avant.gerant)
})

test('compose : memes images pour tous, personnalisation montee en lecture seule', async () => {
  const { sortie, env } = await produire({ version: 'v1.4.0' })
  const compose = readFileSync(path.join(sortie, 'docker-compose.yml'), 'utf8')
  assert.equal(env.TERRAINAPP_VERSION, '1.4.0')
  assert.equal(env.IMAGE_BACKEND, 'ghcr.io/malick2niang/backend_terrainapp')
  assert.match(compose, /image: \$\{IMAGE_BACKEND\}:\$\{TERRAINAPP_VERSION\}/)
  assert.match(compose, /- \.\/complexe:\/app\/complexe:ro/)
  assert.equal((compose.match(/- \.\/marque:\/marque:ro/g) || []).length, 2)
  assert.doesNotMatch(compose, /build:/)
})

test('images locales : nom et tag separes (terrainapp-backend:local)', async () => {
  const { env } = await produire({ imagesLocales: true })
  assert.equal(env.IMAGE_BACKEND, 'terrainapp-backend')
  assert.equal(env.IMAGE_CLIENT, 'terrainapp-client')
  assert.equal(env.TERRAINAPP_VERSION, 'local')
})

test('profil prod par defaut : averti que paiement et SMS simules bloquent le demarrage', async () => {
  const { sortie, env } = await produire()
  assert.equal(env.SPRING_PROFILES_ACTIVE, 'prod')
  assert.match(readFileSync(path.join(sortie, '.env'), 'utf8'), /le backend refusera de demarrer/)
})

test('rien n est ecrit dans le dossier du complexe', async () => {
  const dossier = copieExemple()
  const avant = readdirSync(dossier, { recursive: true }).sort()
  await produire({}, sortieTemp(), dossier)
  assert.deepEqual(readdirSync(dossier, { recursive: true }).sort(), avant)
})

test('arguments de la commande', () => {
  assert.deepEqual(analyserArguments(['exemple', '--profil', 'demo', '--valider']), { profil: 'demo', cible: 'exemple', valider: true })
  assert.throws(() => analyserArguments(['--profil', 'test']), /prod ou demo/)
  assert.throws(() => analyserArguments(['--inconnue']), /option inconnue/)
  assert.equal(resoudreDossier('exemple', tmpdir()), EXEMPLE)
  assert.equal(resoudreDossier(EXEMPLE, '/'), EXEMPLE)
  assert.match(nouveauCode(), /^\w{4}-\w{4}-\w{4}$/)
})

test('commande : code de sortie 1 et erreurs en francais sur un dossier invalide', async () => {
  const dossier = copieExemple()
  const { modifier } = await import('./outils.js')
  modifier(dossier, 'complexe.json', (c) => { c.marque.couleurs.principale = 'bleu' })
  const lignes = []
  const sortie = { log: (l) => lignes.push(l), error: (l) => lignes.push(l) }
  assert.equal(await principal([dossier, '--valider'], sortie), 1)
  assert.ok(lignes.some((l) => l.includes('✗ complexe.json › marque.couleurs.principale : couleur invalide')))
  assert.equal(await principal([EXEMPLE, '--valider'], sortie), 0)
})
