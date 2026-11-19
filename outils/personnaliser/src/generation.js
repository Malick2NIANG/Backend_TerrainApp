// Production du deploiement d'un complexe, a partir de son dossier valide :
//
//   <sortie>/complexe/      complexe.json + donnees/   -> backend (/app/complexe, lecture seule)
//   <sortie>/marque/        logo, icones, favicon, photos, marque.json -> fronts (/marque, lecture seule)
//   <sortie>/.env           secrets (generes une fois, conserves) + reglages
//   <sortie>/docker-compose.yml   images versionnees, identiques pour tous les complexes
//   <sortie>/CODES-ACTIVATION.txt codes a usage unique des nouveaux comptes (a remettre, puis supprimer)
//
// Rejouable : les secrets et codes deja produits sont gardes, le reste est
// regenere depuis le dossier. Rien n'est ecrit dans le dossier du complexe.
import { randomBytes, randomInt } from 'node:crypto'
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import bcrypt from 'bcryptjs'
import sharp from 'sharp'
import { FICHIERS_DONNEES, ICONES } from './validation.js'

export const REGISTRE_PAR_DEFAUT = 'ghcr.io/malick2niang'
export const MOYENS_LIBELLES = { wave: 'Wave', 'orange-money': 'Orange Money', 'mixx-yas': 'Mixx by Yas', carte: 'Carte bancaire' }

// ------------------------------------------------------------------ identite publique

/**
 * Identite publique du complexe : memes champs que GET /api/v1/marque du
 * backend (IdentiteComplexe.Vue), plus les fichiers d'images. Injectee dans
 * /config.js des deux apps au demarrage de leur conteneur.
 */
export function identitePublique(complexe) {
  const m = complexe.marque
  const c = complexe.contact
  const r = complexe.reservation || {}
  const majuscules = (s) => (s ? s.toUpperCase() : undefined)
  const sansVide = (o) => JSON.parse(JSON.stringify(o))
  return sansVide({
    nom: m.nom,
    slogan: m.slogan,
    adresse: c.adresse,
    telephone: c.telephone,
    whatsapp: c.whatsapp,
    email: c.email,
    ville: c.ville,
    personnalise: true,
    nomAccent: m.nomAccent,
    initiales: m.initiales,
    nomComplexe: m.nomComplexe,
    sousTitre: m.sousTitre,
    couleurs: { principale: majuscules(m.couleurs.principale), foncee: majuscules(m.couleurs.foncee), claire: majuscules(m.couleurs.claire) },
    logoUrl: `/marque/${m.logo}`,
    imageAccueilUrl: m.imageAccueil ? `/marque/${m.imageAccueil}` : undefined,
    splashUrl: m.splash ? `/marque/${m.splash}` : undefined,
    gps: c.gps,
    reseauxSociaux: c.reseauxSociaux,
    support: complexe.support,
    moyensPaiement: complexe.paiement.moyens,
    regles: {
      pasCreneauMinutes: r.pasCreneauMinutes ?? 60,
      delaiAnnulationHeures: r.delaiAnnulationHeures,
      margeEntreeMinutes: r.margeEntreeMinutes ?? 30,
    },
    documents: complexe.documents,
    textes: complexe.textes,
    urlClient: complexe.deploiement.urlClient,
    urlManager: complexe.deploiement.urlManager,
  })
}

// ------------------------------------------------------------------ assets

function copierDossier(source, cible) {
  if (!existsSync(source)) return
  mkdirSync(cible, { recursive: true })
  for (const nom of readdirSync(source)) {
    const s = path.join(source, nom)
    const c = path.join(cible, nom)
    if (statSync(s).isDirectory()) copierDossier(s, c)
    else copyFileSync(s, c)
  }
}

/** Icone carree : logo centre sur fond (transparent ou couleur), a `part` de la largeur. */
async function icone(logo, cote, { fond, part = 0.8 } = {}) {
  const interieur = Math.round(cote * part)
  const motif = await sharp(logo, { density: 384 })
    .resize(interieur, interieur, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer()
  return sharp({
    create: { width: cote, height: cote, channels: 4, background: fond || { r: 0, g: 0, b: 0, alpha: 0 } },
  })
    .composite([{ input: motif, gravity: 'centre' }])
    .png()
    .toBuffer()
}

const rgb = (hex) => ({ r: parseInt(hex.slice(1, 3), 16), g: parseInt(hex.slice(3, 5), 16), b: parseInt(hex.slice(5, 7), 16), alpha: 1 })

/**
 * Prepare /marque : fichiers du dossier (logo, photos, annonces, accueil)
 * tels quels, icones PWA et favicon fournis ou generes depuis le logo.
 * Renvoie la liste des fichiers generes.
 */
export async function preparerMarque(dossier, complexe, cible) {
  rmSync(cible, { recursive: true, force: true })
  copierDossier(path.join(dossier, 'assets'), cible)
  const logo = path.join(dossier, 'assets', complexe.marque.logo)
  const principale = rgb(complexe.marque.couleurs.principale)
  const generes = []
  mkdirSync(path.join(cible, 'icons'), { recursive: true })
  for (const [nom, cote] of Object.entries(ICONES)) {
    const p = path.join(cible, 'icons', nom)
    if (existsSync(p)) continue
    // Maskable : zone sure (80 %) sur la couleur de marque ; apple : fond plein (iOS n'aime pas la transparence).
    const options = nom.startsWith('maskable') ? { fond: principale, part: 0.62 }
      : nom.startsWith('apple') ? { fond: { r: 255, g: 255, b: 255, alpha: 1 }, part: 0.82 }
        : { part: 0.92 }
    writeFileSync(p, await icone(logo, cote, options))
    generes.push(`icons/${nom}`)
  }
  if (!existsSync(path.join(cible, 'favicon.svg')) && !existsSync(path.join(cible, 'favicon.png'))) {
    writeFileSync(path.join(cible, 'favicon.png'), await icone(logo, 64, { part: 1 }))
    generes.push('favicon.png')
  }
  writeFileSync(path.join(cible, 'marque.json'), `${JSON.stringify(identitePublique(complexe), null, 2)}\n`)
  return generes
}

// ------------------------------------------------------------------ .env

/** Lit un .env existant (KEY=valeur, sans interpretation). */
export function lireEnv(fichier) {
  const env = {}
  if (!existsSync(fichier)) return env
  for (const ligne of readFileSync(fichier, 'utf8').split(/\r?\n/)) {
    const m = ligne.match(/^([A-Z0-9_]+)=(.*)$/)
    if (m) env[m[1]] = m[2]
  }
  return env
}

const secret = (octets) => randomBytes(octets).toString('hex')

/** Code d'activation lisible : 3 groupes de 4 caracteres, sans 0/O ni 1/I. */
export function nouveauCode() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  const groupe = () => Array.from({ length: 4 }, () => alphabet[randomInt(alphabet.length)]).join('')
  return `${groupe()}-${groupe()}-${groupe()}`
}

/** APP_COMPLEXE_ACTIVATIONS : base64 de { identifiant: empreinte BCrypt }. */
export function lireActivations(valeur) {
  if (!valeur) return {}
  try {
    return JSON.parse(Buffer.from(valeur, 'base64').toString('utf8'))
  } catch {
    return {}
  }
}

const echapperEnv = (v) => String(v ?? '').replace(/[\r\n]/g, ' ')

/**
 * Contenu du .env. Secrets et reglages d'exploitation : gardes s'ils existent
 * deja. Valeurs tirees du dossier (marque, URL, regles) : toujours regenerees.
 * Renvoie { texte, nouveauxCodes: [{ identifiant, role, code }] }.
 */
export function construireEnv({ complexe, donnees }, existant, options) {
  const garder = (cle, defaut) => (existant[cle] !== undefined && existant[cle] !== '' ? existant[cle] : defaut)
  const activations = lireActivations(existant.APP_COMPLEXE_ACTIVATIONS)
  const nouveauxCodes = []
  for (const membre of donnees.personnel || []) {
    if (activations[membre.identifiant]) continue
    const code = nouveauCode()
    activations[membre.identifiant] = bcrypt.hashSync(code, 10)
    nouveauxCodes.push({ identifiant: membre.identifiant, role: membre.role, code })
  }
  const m = complexe.marque
  const c = complexe.contact
  const demo = options.profil === 'demo'
  const version = String(options.version || complexe.deploiement.version || 'latest').replace(/^v/, '')
  const registre = options.registre || REGISTRE_PAR_DEFAUT
  const origines = [complexe.deploiement.urlClient, complexe.deploiement.urlManager]
    .map((u) => new URL(u).origin)
  if (demo) origines.push(`http://localhost:${garder('PORT_CLIENT', '5180')}`, `http://localhost:${garder('PORT_MANAGER', '5181')}`)

  const sections = [
    ['Complexe', {
      COMPLEXE_IDENTIFIANT: complexe.identifiant,
    }],
    ['Images Docker (identiques pour tous les complexes ; une mise a jour = changer la version)', {
      // Images locales (--images-locales) : terrainapp-*:local, construites sur ce poste.
      TERRAINAPP_VERSION: options.imagesLocales ? 'local' : options.version ? version : garder('TERRAINAPP_VERSION', version),
      IMAGE_BACKEND: options.imagesLocales ? 'terrainapp-backend' : garder('IMAGE_BACKEND', `${registre}/backend_terrainapp`),
      IMAGE_CLIENT: options.imagesLocales ? 'terrainapp-client' : garder('IMAGE_CLIENT', `${registre}/client_terrainapp`),
      IMAGE_MANAGER: options.imagesLocales ? 'terrainapp-manager' : garder('IMAGE_MANAGER', `${registre}/manager_terrainapp`),
    }],
    ['Secrets : generes une fois, jamais commites, conserves a chaque relance', {
      MYSQL_ROOT_PASSWORD: garder('MYSQL_ROOT_PASSWORD', secret(24)),
      MYSQL_DATABASE: garder('MYSQL_DATABASE', 'terrainapp'),
      MYSQL_USER: garder('MYSQL_USER', 'terrainapp'),
      MYSQL_PASSWORD: garder('MYSQL_PASSWORD', secret(24)),
      JWT_SECRET: garder('JWT_SECRET', secret(48)),
      // Empreintes BCrypt des codes d'activation (jamais les codes eux-memes).
      APP_COMPLEXE_ACTIVATIONS: Buffer.from(JSON.stringify(activations)).toString('base64'),
    }],
    ['Exploitation', {
      SPRING_PROFILES_ACTIVE: demo ? 'dev' : garder('SPRING_PROFILES_ACTIVE', 'prod'),
      // Jamais les donnees de demonstration (db/demo) : celles du complexe seulement.
      SPRING_FLYWAY_LOCATIONS: 'classpath:db/migration',
      APP_PAIEMENT_PRESTATAIRE: demo ? 'simule' : garder('APP_PAIEMENT_PRESTATAIRE', 'simule'),
      APP_SMS_PRESTATAIRE: demo ? 'simule' : garder('APP_SMS_PRESTATAIRE', 'simule'),
      APP_CORS_ALLOWED_ORIGINS: [...new Set(origines)].join(','),
      PORT_CLIENT: garder('PORT_CLIENT', '5180'),
      PORT_MANAGER: garder('PORT_MANAGER', '5181'),
    }],
    ['Marque (lue au demarrage des conteneurs ; source : complexe.json)', {
      MARQUE_NOM: m.nom,
      MARQUE_NOM_ACCENT: m.nomAccent || '',
      MARQUE_INITIALES: m.initiales || '',
      MARQUE_LOGO_URL: `/marque/${m.logo}`,
      MARQUE_SLOGAN: m.slogan,
      MARQUE_SOUS_TITRE: m.sousTitre || '',
      MARQUE_ETABLISSEMENT: m.nomComplexe,
      MARQUE_COULEUR: m.couleurs.principale.toUpperCase(),
      MARQUE_COULEUR_CLAIRE: (m.couleurs.claire || '').toUpperCase(),
      MARQUE_ADRESSE: [c.adresse, c.ville].filter(Boolean).join(', '),
      MARQUE_VILLE: c.ville,
      MARQUE_TELEPHONE: c.telephone,
      MARQUE_WHATSAPP: c.whatsapp || '',
      MARQUE_EMAIL: c.email,
      SUPPORT_TELEPHONE: complexe.support?.telephone || c.telephone,
      SUPPORT_EMAIL: complexe.support?.email || c.email,
      URL_APP_CLIENT: complexe.deploiement.urlClient,
      APP_TICKET_MARGE_AVANT: String(complexe.reservation?.margeEntreeMinutes ?? 30),
    }],
  ]
  let texte = `# Deploiement de « ${m.nomComplexe} » : genere par npm run client -- ${complexe.identifiant}.\n`
    + '# CONTIENT DES SECRETS : ne jamais commiter, ne jamais envoyer par messagerie.\n'
    + '# Variables documentees dans outils/personnaliser/.env.exemple.\n'
  if (!demo && garder('APP_PAIEMENT_PRESTATAIRE', 'simule') === 'simule') {
    texte += '# ATTENTION : profil prod avec paiement/SMS simules -> le backend refusera de demarrer\n'
      + '# tant que les prestataires reels ne sont pas branches (ou utilisez --profil demo).\n'
  }
  for (const [titre, valeurs] of sections) {
    texte += `\n# --- ${titre}\n`
    for (const [cle, valeur] of Object.entries(valeurs)) texte += `${cle}=${echapperEnv(valeur)}\n`
  }
  return { texte, nouveauxCodes }
}

// ------------------------------------------------------------------ docker compose

/** docker-compose.yml du complexe : memes images pour tous, la personnalisation est montee. */
export function construireCompose(complexe) {
  return `# Deploiement de « ${complexe.marque.nomComplexe} » (genere par npm run client).
# Images identiques pour tous les complexes : la personnalisation est lue au
# demarrage depuis ./complexe (backend) et ./marque (apps), montes en lecture seule.
#   docker compose --env-file .env up -d --wait
name: terrainapp-${complexe.identifiant}

services:
  mysql:
    image: mysql:8.0
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: \${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: \${MYSQL_DATABASE}
      MYSQL_USER: \${MYSQL_USER}
      MYSQL_PASSWORD: \${MYSQL_PASSWORD}
    volumes:
      - mysql:/var/lib/mysql
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h localhost -u root -p$$MYSQL_ROOT_PASSWORD"]
      interval: 5s
      timeout: 5s
      retries: 20

  backend:
    image: \${IMAGE_BACKEND}:\${TERRAINAPP_VERSION}
    restart: unless-stopped
    depends_on:
      mysql:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: \${SPRING_PROFILES_ACTIVE}
      SPRING_FLYWAY_LOCATIONS: \${SPRING_FLYWAY_LOCATIONS}
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/\${MYSQL_DATABASE}
      SPRING_DATASOURCE_USERNAME: \${MYSQL_USER}
      SPRING_DATASOURCE_PASSWORD: \${MYSQL_PASSWORD}
      APP_JWT_SECRET: \${JWT_SECRET}
      APP_COMPLEXE_DOSSIER: /app/complexe
      APP_COMPLEXE_ACTIVATIONS: \${APP_COMPLEXE_ACTIVATIONS}
      APP_PAIEMENT_PRESTATAIRE: \${APP_PAIEMENT_PRESTATAIRE}
      APP_SMS_PRESTATAIRE: \${APP_SMS_PRESTATAIRE}
      APP_TICKET_MARGE_AVANT: \${APP_TICKET_MARGE_AVANT}
      APP_CORS_ALLOWED_ORIGINS: \${APP_CORS_ALLOWED_ORIGINS}
      MARQUE_NOM: \${MARQUE_NOM}
      MARQUE_SLOGAN: \${MARQUE_SLOGAN}
      MARQUE_ADRESSE: \${MARQUE_ADRESSE}
      MARQUE_VILLE: \${MARQUE_VILLE}
      MARQUE_TELEPHONE: \${MARQUE_TELEPHONE}
      MARQUE_WHATSAPP: \${MARQUE_WHATSAPP}
      MARQUE_EMAIL: \${MARQUE_EMAIL}
    volumes:
      - ./complexe:/app/complexe:ro
      - photos:/app/donnees/photos
      - videos:/app/donnees/videos

${['client', 'manager'].map((app) => `  ${app}:
    image: \${IMAGE_${app.toUpperCase()}}:\${TERRAINAPP_VERSION}
    restart: unless-stopped
    depends_on:
      backend:
        condition: service_healthy
    environment:
      API_UPSTREAM: http://backend:8080
      SUPPORT_TELEPHONE: \${SUPPORT_TELEPHONE}
      SUPPORT_EMAIL: \${SUPPORT_EMAIL}
      MARQUE_NOM: \${MARQUE_NOM}
      MARQUE_NOM_ACCENT: \${MARQUE_NOM_ACCENT}
      MARQUE_INITIALES: \${MARQUE_INITIALES}
      MARQUE_LOGO_URL: \${MARQUE_LOGO_URL}
      MARQUE_COULEUR: \${MARQUE_COULEUR}
      MARQUE_COULEUR_CLAIRE: \${MARQUE_COULEUR_CLAIRE}
${app === 'client' ? `      MARQUE_SLOGAN: \${MARQUE_SLOGAN}
      MARQUE_SOUS_TITRE: \${MARQUE_SOUS_TITRE}
      MARQUE_ADRESSE: \${MARQUE_ADRESSE}
      MARQUE_WHATSAPP: \${MARQUE_WHATSAPP}` : `      MARQUE_ETABLISSEMENT: \${MARQUE_ETABLISSEMENT}
      URL_APP_CLIENT: \${URL_APP_CLIENT}`}
    ports:
      - "\${PORT_${app.toUpperCase()}}:8080"
    volumes:
      - ./marque:/marque:ro
`).join('\n')}
volumes:
  mysql:
  photos:
  videos:
`
}

// ------------------------------------------------------------------ ensemble

/** Produit le deploiement complet dans `sortie`. */
export async function generer(dossier, lu, sortie, options = {}) {
  mkdirSync(sortie, { recursive: true })
  // Garde-fou : ce dossier contient des secrets.
  writeFileSync(path.join(sortie, '.gitignore'), '# Secrets du deploiement : jamais dans git.\n*\n')

  const cibleComplexe = path.join(sortie, 'complexe')
  rmSync(cibleComplexe, { recursive: true, force: true })
  mkdirSync(path.join(cibleComplexe, 'donnees'), { recursive: true })
  copyFileSync(path.join(dossier, 'complexe.json'), path.join(cibleComplexe, 'complexe.json'))
  for (const nom of FICHIERS_DONNEES) {
    const f = path.join(dossier, 'donnees', `${nom}.json`)
    if (existsSync(f)) copyFileSync(f, path.join(cibleComplexe, 'donnees', `${nom}.json`))
  }

  const generes = await preparerMarque(dossier, lu.complexe, path.join(sortie, 'marque'))

  const fichierEnv = path.join(sortie, '.env')
  const { texte, nouveauxCodes } = construireEnv(lu, lireEnv(fichierEnv), options)
  writeFileSync(fichierEnv, texte)
  writeFileSync(path.join(sortie, 'docker-compose.yml'), construireCompose(lu.complexe))

  if (nouveauxCodes.length) {
    const date = new Date().toISOString().slice(0, 16).replace('T', ' ')
    let codes = existsSync(path.join(sortie, 'CODES-ACTIVATION.txt')) ? readFileSync(path.join(sortie, 'CODES-ACTIVATION.txt'), 'utf8') : ''
    codes += `# ${date} — codes a usage unique : a remettre en main propre, puis SUPPRIMER ce fichier.\n`
      + '# Premiere connexion au Manager : identifiant + code, puis choix du mot de passe (12 caracteres min.).\n'
    for (const n of nouveauxCodes) codes += `${n.identifiant.padEnd(20)} ${n.role.padEnd(11)} ${n.code}\n`
    writeFileSync(path.join(sortie, 'CODES-ACTIVATION.txt'), codes)
  }
  return { generes, nouveauxCodes }
}
