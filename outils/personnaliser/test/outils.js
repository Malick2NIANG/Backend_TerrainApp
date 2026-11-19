// Outils des tests : copie de clients/exemple dans un dossier temporaire,
// modification d'un fichier JSON de la copie.
import { cpSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const EXEMPLE = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../clients/exemple')

export function copieExemple() {
  const dossier = path.join(mkdtempSync(path.join(tmpdir(), 'complexe-')), 'exemple')
  cpSync(EXEMPLE, dossier, { recursive: true })
  return dossier
}

export function modifier(dossier, fichier, modification) {
  const p = path.join(dossier, fichier)
  const json = JSON.parse(readFileSync(p, 'utf8'))
  const resultat = modification(json)
  writeFileSync(p, JSON.stringify(resultat ?? json, null, 2))
  return dossier
}
