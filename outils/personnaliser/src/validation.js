// Validation du dossier d'un complexe (clients/<id>/) : schema JSON, regles
// qui croisent plusieurs fichiers, puis fichiers d'assets (presence, format,
// taille). Toutes les erreurs sont collectees, chacune en francais avec le
// fichier et le champ a corriger.
import { existsSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import Ajv2020 from 'ajv/dist/2020.js'
import sharp from 'sharp'

const ICI = path.dirname(fileURLToPath(import.meta.url))
const SCHEMAS = path.resolve(ICI, '../schema')

export const JOURS = ['lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi', 'dimanche']
export const FICHIERS_DONNEES = ['sports', 'terrains', 'abonnements', 'annonces', 'personnel']

/** Icones PWA attendues dans assets/icons/ (generees depuis le logo si absentes). */
export const ICONES = {
  'icon-192.png': 192,
  'icon-512.png': 512,
  'maskable-512.png': 512,
  'apple-touch-icon.png': 180,
}

const Mo = 1024 * 1024

// ------------------------------------------------------------------ schema

let valideurs = null

function chargerValideurs() {
  if (valideurs) return valideurs
  const ajv = new Ajv2020({ allErrors: true, verbose: true, strict: true })
  ajv.addKeyword({ keyword: 'x-erreur', schemaType: 'string' })
  const complexe = JSON.parse(readFileSync(path.join(SCHEMAS, 'complexe.schema.json'), 'utf8'))
  const donnees = JSON.parse(readFileSync(path.join(SCHEMAS, 'donnees.schema.json'), 'utf8'))
  ajv.addSchema(complexe)
  ajv.addSchema(donnees)
  valideurs = { complexe: ajv.getSchema(complexe.$id) }
  for (const nom of FICHIERS_DONNEES) {
    valideurs[nom] = ajv.getSchema(`${donnees.$id}#/$defs/${nom}`)
  }
  return valideurs
}

/** "/marque/couleurs/0/nom" -> "marque.couleurs[0].nom" */
export function cheminLisible(instancePath) {
  return instancePath
    .split('/')
    .filter(Boolean)
    .map((p) => p.replace(/~1/g, '/').replace(/~0/g, '~'))
    .reduce((acc, p) => (/^\d+$/.test(p) ? `${acc}[${p}]` : acc ? `${acc}.${p}` : p), '')
}

const TYPES = { string: 'du texte', integer: 'un nombre entier', number: 'un nombre', array: 'une liste', object: 'un objet', boolean: 'true ou false' }

function valeurLisible(v) {
  if (v === undefined) return ''
  const t = typeof v === 'string' ? v : JSON.stringify(v)
  return t.length > 40 ? `${t.slice(0, 40)}…` : t
}

/** Message francais d'une erreur ajv. */
function message(e) {
  const perso = e.parentSchema?.['x-erreur']
  const valeur = ['object'].includes(typeof e.data) ? '' : ` (valeur : « ${valeurLisible(e.data)} »)`
  switch (e.keyword) {
    case 'required':
      return { champ: e.params.missingProperty, texte: 'champ manquant' }
    case 'additionalProperties':
      return { champ: e.params.additionalProperty, texte: 'champ inconnu (faute de frappe ?)' }
    case 'type':
      return { texte: `${TYPES[e.params.type] || e.params.type} attendu${valeur}` }
    case 'minLength':
      return { texte: e.params.limit === 1 ? 'ne doit pas être vide' : `${e.params.limit} caractères au minimum` }
    case 'maxLength':
      return { texte: `${e.params.limit} caractères au maximum` }
    case 'minItems':
      return { texte: `${e.params.limit} élément(s) au minimum` }
    case 'maxItems':
      return { texte: `${e.params.limit} éléments au maximum` }
    case 'uniqueItems':
      return { texte: 'une valeur est en double' }
    case 'not':
      return { texte: perso || 'champ interdit' }
    default:
      if (perso) return { texte: perso + valeur }
      if (e.keyword === 'minimum') return { texte: `doit être supérieur ou égal à ${e.params.limit}${valeur}` }
      if (e.keyword === 'maximum') return { texte: `doit être inférieur ou égal à ${e.params.limit}${valeur}` }
      if (e.keyword === 'enum') return { texte: `valeur inattendue${valeur} (attendu : ${e.params.allowedValues.join(', ')})` }
      return { texte: `${e.message}${valeur}` }
  }
}

/** Erreurs ajv -> messages "fichier › champ : texte", sans doublons ni bruit des oneOf. */
export function formaterErreurs(fichier, erreurs) {
  // oneOf (horaires : "ferme" OU { ouverture, fermeture }) : pour un objet,
  // on garde les erreurs precises de la branche objet ; sinon le message
  // d'ensemble du oneOf.
  const oneOf = erreurs.filter((e) => e.keyword === 'oneOf')
  const objet = (o) => o.data !== null && typeof o.data === 'object'
  const utiles = erreurs.filter((e) => {
    if (e.keyword === 'oneOf') return !objet(e)
    const parent = oneOf.find((o) => e.schemaPath.startsWith(`${o.schemaPath}/`) && e.instancePath.startsWith(o.instancePath))
    if (!parent) return true
    return objet(parent) && !e.schemaPath.startsWith(`${parent.schemaPath}/0/`)
  })
  const vus = new Set()
  const sortie = []
  for (const e of utiles) {
    const { champ, texte } = message(e)
    const chemin = [cheminLisible(e.instancePath), champ].filter(Boolean).join('.') || '(racine)'
    const ligne = `${fichier} › ${chemin.replace(/\.\[/g, '[')} : ${texte}`
    if (!vus.has(ligne)) {
      vus.add(ligne)
      sortie.push(ligne)
    }
  }
  return sortie
}

// ------------------------------------------------------------------ lecture

function lireJson(dossier, fichier, erreurs, obligatoire) {
  const p = path.join(dossier, fichier)
  if (!existsSync(p)) {
    if (obligatoire) erreurs.push(`${fichier} : fichier manquant`)
    return undefined
  }
  try {
    // Fichier enregistre avec BOM (Bloc-notes Windows) : BOM retire.
    const texte = readFileSync(p, 'utf8')
    return JSON.parse(texte.charCodeAt(0) === 0xfeff ? texte.slice(1) : texte)
  } catch (e) {
    erreurs.push(`${fichier} : JSON illisible (${e.message})`)
    return undefined
  }
}

/** Lit le dossier : { complexe, donnees: { sports, terrains, ... }, erreurs } (schema seulement). */
export function lireDossier(dossier) {
  const erreurs = []
  if (!existsSync(dossier) || !statSync(dossier).isDirectory()) {
    return { complexe: undefined, donnees: {}, erreurs: [`dossier introuvable : ${dossier}`] }
  }
  const v = chargerValideurs()
  const complexe = lireJson(dossier, 'complexe.json', erreurs, true)
  if (complexe !== undefined && !v.complexe(complexe)) {
    erreurs.push(...formaterErreurs('complexe.json', v.complexe.errors))
  }
  const donnees = {}
  for (const nom of FICHIERS_DONNEES) {
    const f = `donnees/${nom}.json`
    const contenu = lireJson(dossier, f, erreurs, false)
    if (contenu === undefined) {
      donnees[nom] = []
      continue
    }
    if (!v[nom](contenu)) {
      erreurs.push(...formaterErreurs(f, v[nom].errors))
      donnees[nom] = Array.isArray(contenu) ? contenu : []
    } else {
      donnees[nom] = contenu
    }
  }
  return { complexe, donnees, erreurs }
}

// ------------------------------------------------------------------ regles croisees

function verifierHoraires(f, champ, horaires, erreurs) {
  if (!horaires || typeof horaires !== 'object') return
  for (const j of JOURS) {
    const h = horaires[j]
    if (h && typeof h === 'object' && h.ouverture && h.ouverture === h.fermeture) {
      erreurs.push(`${f} › ${champ}.${j} : l'ouverture et la fermeture doivent être différentes`)
    }
  }
}

function clesUniques(f, liste, champ, erreurs) {
  const vues = new Set()
  liste.forEach((el, i) => {
    const v = el?.[champ]
    if (typeof v !== 'string') return
    if (vues.has(v)) erreurs.push(`${f} › [${i}].${champ} : « ${v} » en double`)
    vues.add(v)
  })
  return vues
}

/**
 * Regles que le schema ne peut pas exprimer seul : references entre fichiers,
 * cles uniques, durees compatibles avec le pas des creneaux...
 * Renvoie { erreurs, avertissements }.
 */
export function verifierRegles({ complexe, donnees }, maintenant = new Date()) {
  const erreurs = []
  const avertissements = []
  if (!complexe || typeof complexe !== 'object') return { erreurs, avertissements }
  const C = 'complexe.json'
  const m = complexe.marque || {}
  if (m.nomAccent && m.nom && !m.nom.toLowerCase().endsWith(m.nomAccent.toLowerCase())) {
    erreurs.push(`${C} › marque.nomAccent : doit être la fin du nom (« ${m.nom} »)`)
  }
  const gps = complexe.contact?.gps
  if (gps && gps.latitude === 0 && gps.longitude === 0) {
    erreurs.push(`${C} › contact.gps : position 0,0 (en plein océan) : position non renseignée ?`)
  }
  verifierHoraires(C, 'horaires', complexe.horaires, erreurs)
  const pas = complexe.reservation?.pasCreneauMinutes ?? 60

  const { sports = [], terrains = [], abonnements = [], annonces = [], personnel = [] } = donnees
  const clesSports = clesUniques('donnees/sports.json', sports, 'cle', erreurs)
  const noms = new Set()
  sports.forEach((s, i) => {
    const n = String(s?.nom ?? '').trim().toLowerCase()
    if (n && noms.has(n)) erreurs.push(`donnees/sports.json › [${i}].nom : sport « ${s.nom} » en double`)
    noms.add(n)
  })

  const T = 'donnees/terrains.json'
  const clesTerrains = clesUniques(T, terrains, 'cle', erreurs)
  terrains.forEach((t, i) => {
    const ici = `[${i}]${t?.nom ? ` (${t.nom})` : ''}`
    if (t?.sport && !clesSports.has(t.sport)) {
      erreurs.push(`${T} › ${ici}.sport : sport « ${t.sport} » absent de donnees/sports.json`)
    }
    for (const d of t?.dureesProposees || []) {
      if (Number.isInteger(d) && d % pas !== 0) {
        erreurs.push(`${T} › ${ici}.dureesProposees : durée ${d} min incompatible avec le pas des créneaux (${pas} min) : choisissez un multiple de ${pas}`)
      }
    }
    ;(t?.plages || []).forEach((p, j) => {
      if (p?.debut && p.debut === p.fin) erreurs.push(`${T} › ${ici}.plages[${j}] : le début et la fin doivent être différents`)
    })
    verifierHoraires(T, `${ici}.horaires`, t?.horaires, erreurs)
    if (t?.gps && t.gps.latitude === 0 && t.gps.longitude === 0) {
      erreurs.push(`${T} › ${ici}.gps : position 0,0 : position non renseignée ?`)
    }
    if (!t?.photos?.length) avertissements.push(`${T} › ${ici} : aucune photo (la fiche du terrain sera moins attirante)`)
  })

  const A = 'donnees/abonnements.json'
  clesUniques(A, abonnements, 'cle', erreurs)
  abonnements.forEach((a, i) => {
    if (a?.terrain && !clesTerrains.has(a.terrain)) {
      erreurs.push(`${A} › [${i}].terrain : terrain « ${a.terrain} » absent de donnees/terrains.json`)
    }
    if (a?.reglement === 'AVANCE' && !a.moyenPaiement) {
      erreurs.push(`${A} › [${i}].moyenPaiement : obligatoire pour un règlement d'avance (AVANCE)`)
    }
    if (a?.premiereSeance && new Date(`${a.premiereSeance}:00Z`) < maintenant) {
      avertissements.push(`${A} › [${i}].premiereSeance : date passée (${a.premiereSeance}) : le chargement échouera s'il n'a pas déjà eu lieu`)
    }
  })

  clesUniques('donnees/annonces.json', annonces, 'cle', erreurs)
  clesUniques('donnees/personnel.json', personnel, 'identifiant', erreurs)
  if (personnel.length && !personnel.some((p) => p?.role === 'GERANT' || p?.role === 'ADMIN')) {
    avertissements.push('donnees/personnel.json : aucun compte GERANT ni ADMIN (personne ne pourra gérer le complexe)')
  }
  return { erreurs, avertissements }
}

// ------------------------------------------------------------------ assets

async function image(fichier) {
  try {
    const m = await sharp(fichier).metadata()
    return { largeur: m.width, hauteur: m.height, format: m.format }
  } catch {
    return null
  }
}

async function verifierImage(dossier, relatif, regles, erreurs, avertissements) {
  const p = path.join(dossier, 'assets', relatif)
  const f = `assets/${relatif}`
  if (!existsSync(p)) {
    erreurs.push(`${f} : fichier absent (${regles.role})`)
    return
  }
  const taille = statSync(p).size
  if (taille > regles.maxOctets) {
    erreurs.push(`${f} : fichier trop lourd (${(taille / Mo).toFixed(1)} Mo, ${regles.maxOctets / Mo} Mo au maximum)`)
  }
  if (/\.(mp4|webm)$/.test(relatif)) return
  const img = await image(p)
  if (!img) {
    erreurs.push(`${f} : image illisible ou format non pris en charge (PNG, JPEG, WebP ou SVG)`)
    return
  }
  if (regles.carre && Math.abs(img.largeur / img.hauteur - 1) > 0.1) {
    erreurs.push(`${f} : ${regles.role} carré attendu (${img.largeur} × ${img.hauteur} px)`)
  }
  if (regles.exact && (img.largeur !== regles.exact || img.hauteur !== regles.exact)) {
    erreurs.push(`${f} : ${regles.exact} × ${regles.exact} px attendus (${img.largeur} × ${img.hauteur} px)`)
  }
  if (regles.minLargeur && img.format !== 'svg' && img.largeur < regles.minLargeur) {
    const texte = `${f} : trop petite (${img.largeur} px de large, ${regles.minLargeur} px au minimum conseillés)`
    ;(regles.minBloquant ? erreurs : avertissements).push(texte)
  }
}

/** Presence, format et taille des fichiers references par le dossier. */
export async function verifierAssets(dossier, { complexe, donnees }) {
  const erreurs = []
  const avertissements = []
  const m = complexe?.marque || {}
  if (typeof m.logo === 'string') {
    await verifierImage(dossier, m.logo, { role: 'logo', carre: true, minLargeur: 512, minBloquant: true, maxOctets: Mo }, erreurs, avertissements)
  }
  if (typeof m.imageAccueil === 'string') {
    await verifierImage(dossier, m.imageAccueil, { role: "image d'accueil", minLargeur: 1200, maxOctets: 3 * Mo }, erreurs, avertissements)
  }
  if (typeof m.splash === 'string') {
    await verifierImage(dossier, m.splash, { role: 'écran de démarrage', minLargeur: 720, maxOctets: 2 * Mo }, erreurs, avertissements)
  }
  for (const [nom, cote] of Object.entries(ICONES)) {
    if (existsSync(path.join(dossier, 'assets/icons', nom))) {
      await verifierImage(dossier, `icons/${nom}`, { role: 'icône', exact: cote, maxOctets: Mo }, erreurs, avertissements)
    }
  }
  for (const nom of ['favicon.svg', 'favicon.png']) {
    if (existsSync(path.join(dossier, 'assets', nom))) {
      await verifierImage(dossier, nom, { role: 'favicon', carre: true, maxOctets: Mo / 4 }, erreurs, avertissements)
    }
  }
  for (const t of donnees.terrains || []) {
    for (const photo of Array.isArray(t?.photos) ? t.photos : []) {
      if (typeof photo !== 'string') continue
      const video = /\.(mp4|webm)$/.test(photo)
      await verifierImage(dossier, `terrains/${photo}`, {
        role: `photo du terrain « ${t.nom} »`, minLargeur: 800, maxOctets: video ? 30 * Mo : 2 * Mo,
      }, erreurs, avertissements)
    }
  }
  for (const a of donnees.annonces || []) {
    for (const s of Array.isArray(a?.slides) ? a.slides : []) {
      if (typeof s?.media !== 'string') continue
      const video = /\.(mp4|webm)$/.test(s.media)
      await verifierImage(dossier, `annonces/${s.media}`, {
        role: `média de l'annonce « ${a.titre} »`, minLargeur: 720, maxOctets: video ? 30 * Mo : 2 * Mo,
      }, erreurs, avertissements)
    }
  }
  return { erreurs, avertissements }
}

/** Validation complete : { dossier lu, erreurs, avertissements }. */
export async function valider(dossier, options = {}) {
  const lu = lireDossier(dossier)
  const regles = verifierRegles(lu, options.maintenant)
  const assets = lu.complexe ? await verifierAssets(dossier, lu) : { erreurs: [], avertissements: [] }
  return {
    ...lu,
    erreurs: [...lu.erreurs, ...regles.erreurs, ...assets.erreurs],
    avertissements: [...regles.avertissements, ...assets.avertissements],
  }
}
