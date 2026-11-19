#!/usr/bin/env node
// npm run client -- <chemin-ou-identifiant> [options]
//
// Valide le dossier d'un complexe puis produit son deploiement (voir
// PERSONNALISER-UN-COMPLEXE.md a la racine du depot).
import { existsSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { valider } from './validation.js'
import { generer } from './generation.js'

const ICI = path.dirname(fileURLToPath(import.meta.url))
const RACINE = path.resolve(ICI, '../../..')

const AIDE = `Usage : npm run client -- <chemin-ou-identifiant> [options]

  <chemin-ou-identifiant>   dossier du complexe (ex. ../../../Clients_TerrainApp/clients/arena-thies)
                            ou identifiant d'un dossier de clients/ (ex. exemple)

Options :
  --valider                 valide seulement, ne produit rien
  --sortie <dossier>        dossier du deploiement (defaut : deploiements/<identifiant>/ a la racine du depot)
  --profil prod|demo        prod (defaut) ; demo : paiement et SMS simules, ports locaux autorises
  --version <x.y.z>         version des images Docker (defaut : deploiement.version du dossier, sinon latest)
  --registre <hote/compte>  registre des images (defaut : ghcr.io/malick2niang)
  --images-locales          images construites sur ce poste (terrainapp-*:local), pour essayer sans registre
  --aide                    cette aide`

export function analyserArguments(args) {
  const o = { profil: 'prod' }
  for (let i = 0; i < args.length; i++) {
    const a = args[i]
    const suivant = () => {
      if (i + 1 >= args.length) throw new Error(`option ${a} : valeur manquante`)
      return args[++i]
    }
    if (a === '--valider') o.valider = true
    else if (a === '--sortie') o.sortie = suivant()
    else if (a === '--profil') o.profil = suivant()
    else if (a === '--version') o.version = suivant()
    else if (a === '--registre') o.registre = suivant()
    else if (a === '--images-locales') o.imagesLocales = true
    else if (a === '--aide' || a === '-h' || a === '--help') o.aide = true
    else if (a.startsWith('--')) throw new Error(`option inconnue : ${a}`)
    else if (!o.cible) o.cible = a
    else throw new Error(`argument en trop : ${a}`)
  }
  if (!['prod', 'demo'].includes(o.profil)) throw new Error(`--profil : prod ou demo attendu (recu : ${o.profil})`)
  return o
}

/** Chemin d'un dossier existant, sinon clients/<identifiant> du depot. */
export function resoudreDossier(cible, base = process.env.INIT_CWD || process.cwd()) {
  const direct = path.resolve(base, cible)
  if (existsSync(direct) && statSync(direct).isDirectory()) return direct
  return path.join(RACINE, 'clients', cible)
}

export async function principal(args, sortieConsole = console) {
  let o
  try {
    o = analyserArguments(args)
  } catch (e) {
    sortieConsole.error(`Erreur : ${e.message}\n\n${AIDE}`)
    return 2
  }
  if (o.aide || !o.cible) {
    sortieConsole.log(AIDE)
    return o.aide ? 0 : 2
  }
  const dossier = resoudreDossier(o.cible)
  sortieConsole.log(`Dossier du complexe : ${dossier}`)
  const r = await valider(dossier)
  for (const a of r.avertissements) sortieConsole.log(`  ! ${a}`)
  if (r.erreurs.length) {
    sortieConsole.error(`\n${r.erreurs.length} erreur(s) a corriger :`)
    for (const e of r.erreurs) sortieConsole.error(`  ✗ ${e}`)
    return 1
  }
  sortieConsole.log(`✓ Configuration valide : ${r.complexe.marque.nomComplexe} (${r.donnees.terrains.length} terrain(s), `
    + `${r.donnees.personnel.length} compte(s) du personnel)`)
  if (o.valider) return 0

  const sortie = o.sortie
    ? path.resolve(process.env.INIT_CWD || process.cwd(), o.sortie)
    : path.join(RACINE, 'deploiements', r.complexe.identifiant)
  const { generes, nouveauxCodes } = await generer(dossier, r, sortie, o)
  sortieConsole.log(`✓ Deploiement produit dans ${sortie}`)
  if (generes.length) sortieConsole.log(`  genere depuis le logo : ${generes.join(', ')}`)
  if (nouveauxCodes.length) {
    sortieConsole.log('\nCodes d\'activation (a usage unique, affiches une seule fois, aussi dans CODES-ACTIVATION.txt) :')
    for (const n of nouveauxCodes) sortieConsole.log(`  ${n.identifiant.padEnd(20)} ${n.role.padEnd(11)} ${n.code}`)
  }
  sortieConsole.log(`\nDeployer :\n  cd "${sortie}"\n  docker compose up -d --wait`)
  if (o.profil === 'prod') {
    sortieConsole.log('  (profil prod : paiement et SMS reels requis ; pour une demonstration, relancez avec --profil demo)')
  }
  return 0
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  principal(process.argv.slice(2)).then((code) => { process.exitCode = code })
}
