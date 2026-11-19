# Personnaliser TerrainApp pour un complexe

Livrer TerrainApp à un nouveau complexe, c'est **remplir un dossier** et **lancer une commande**.
On ne touche ni au code ni aux images Docker. Les images restent les mêmes pour tous les complexes :
la personnalisation est lue au démarrage des conteneurs. Une mise à jour de TerrainApp profite donc à
tous les clients, il suffit de changer la version.

```
clients/<identifiant>/          ← ce que vous remplissez (sans aucun secret)
  complexe.json                 identité, contacts, horaires, paiement, règles
  assets/                       logo, image d'accueil, photos, icônes (facultatives)
  donnees/                      sports, terrains, abonnements, annonces, personnel

npm run client -- <identifiant ou chemin>
        │  valide tout (messages en français)
        ▼
deploiements/<identifiant>/     ← produit, jamais dans git (contient des secrets)
  docker-compose.yml  .env  complexe/  marque/  CODES-ACTIVATION.txt
```

Sans dossier de complexe, rien ne change : la marque par défaut TerrainApp s'applique comme avant.

---

## 1. Ce qu'il faut demander au complexe

| Élément | Format attendu | Où ça va |
|---|---|---|
| **Logo** | Carré, **PNG transparent 1024 × 1024 px** (512 px minimum) ou **SVG**, 1 Mo maximum. Version lisible en petit (pas de texte fin). | `assets/logo.png` ou `assets/logo.svg` |
| Couleurs | Couleur principale en `#RRGGBB` (celle du logo ou du maillot). Facultatif : une teinte foncée et une teinte claire. Sans elles, elles sont calculées. | `marque.couleurs` |
| Nom | Nom court (40 caractères maximum, affiché partout), nom complet du complexe, slogan d'une phrase. | `marque` |
| **Photos des terrains** | **JPEG, paysage, 1600 px de large** (800 px minimum), 2 Mo maximum chacune, 1 à 10 par terrain. Prises de jour, terrain entier visible, sans personnes reconnaissables. La première sert de couverture. | `assets/terrains/` |
| Image d'accueil (facultative) | JPEG paysage, 1600 × 900 px minimum, 3 Mo maximum. Sinon, la photo du terrain le plus réservé. | `assets/accueil.jpg` |
| Icônes de l'app (facultatives) | PNG exacts : 192 × 192, 512 × 512, maskable 512 × 512 (motif dans les 80 % centraux), apple-touch 180 × 180. **Générées depuis le logo si absentes.** | `assets/icons/` |
| Favicon (facultatif) | SVG ou PNG carré. Sinon, généré depuis le logo. | `assets/favicon.svg` |
| **Tarifs** | Prix à l'heure de chaque terrain, en F CFA entiers. Tarifs particuliers éventuels (soirée, week-end) : jours, heure de début, heure de fin, prix. | `donnees/terrains.json` |
| Durées proposées | Pour chaque terrain, parmi 30 min, 1 h, 1 h 30, 2 h, 2 h 30 et 3 h. Elles doivent être des multiples du pas des créneaux. | `dureesProposees` |
| **Horaires** | Ouverture et fermeture pour chacun des 7 jours, ou « fermé ». Une fermeture après minuit s'écrit `"fermeture": "01:00"`. Horaires propres à un terrain si besoin (padel fermé plus tôt…). | `horaires` |
| **Position GPS** | Latitude et longitude du portail d'entrée. Dans Google Maps : appui long sur l'entrée, puis copier les deux nombres (ex. `14.7910, -16.9256`). | `contact.gps` |
| Contacts | Téléphone, WhatsApp, e-mail, adresse, ville, réseaux sociaux (liens `https://`). | `contact` |
| Paiement | Moyens en ligne acceptés parmi Wave, Orange Money, Mixx by Yas et carte. Les espèces restent possibles au comptoir. | `paiement.moyens` |
| Règles | Pas des créneaux (30, 60, 90 ou 120 min). Délai d'annulation en heures : facultatif, sans lui aucune règle. Marge d'entrée avant le créneau (30 min par défaut). | `reservation` |
| Tickets et reçus | Nom imprimé en tête (40 caractères maximum), phrase de pied (« Merci et bon match ! »). | `documents` |
| Personnel | Pour chaque compte : identifiant, rôle (gérant, accueil, contrôleur), prénom, nom. **Aucun mot de passe.** | `donnees/personnel.json` |
| Abonnements en cours (facultatif) | Clubs, académies, entreprises déjà clients : terrain, jour et heure, nombre de séances, règlement. | `donnees/abonnements.json` |
| Annonce de lancement (facultative) | Titre, message, image carrée 1080 px. | `donnees/annonces.json`, `assets/annonces/` |
| Adresses web | Adresse de l'app des joueurs et du back-office (ex. `reserver.mon-complexe.sn`, `gestion.mon-complexe.sn`). | `deploiement` |

Les noms de fichiers s'écrivent en **minuscules, chiffres et tirets**, sans espace ni accent
(`terrain-a-1.jpg`).

---

## 2. Remplir le dossier

1. Copiez `clients/exemple/` et renommez la copie avec l'identifiant du complexe (`arena-thies`).
   L'identifiant ne change plus ensuite.
2. Remplacez le contenu de `complexe.json`, de `assets/` et de `donnees/*.json`. Dans VS Code, la
   ligne `"$schema"` de `complexe.json` donne l'autocomplétion et souligne les erreurs pendant la
   saisie.
3. Chaque élément de `donnees/` porte une **clé** (`"cle": "terrain-a"`). Elle identifie l'élément
   pour toujours : **ne la changez jamais** après le premier déploiement. Pour modifier un terrain
   déjà livré, passez par le Manager, pas par le dossier.

Fichiers de `donnees/`, tous facultatifs :

| Fichier | Contenu |
|---|---|
| `sports.json` | `cle`, `nom`, `ordre` |
| `terrains.json` | `cle`, `sport` (clé du sport), `nom`, `description`, `localite`, `gps`, `surface`, `capacite`, `equipements`, `photos` (fichiers de `assets/terrains/`), `tarifHeure`, `dureesProposees`, `plages`, `horaires`, `publie`, `ordre` |
| `abonnements.json` | `cle`, `terrain`, `client` (`nom`, `telephone`), `frequence` (`HEBDOMADAIRE` ou `MENSUELLE`), `jours`, `premiereSeance` (`2027-01-05T17:00`, heure du complexe), `nombreSeances`, `reglement` (`AVANCE` ou `SEANCE`), `moyenPaiement`, `remise` |
| `annonces.json` | `cle`, `titre`, `publiee`, `slides` (`titre`, `message`, `media` de `assets/annonces/`) |
| `personnel.json` | `identifiant`, `role` (`GERANT`, `ACCUEIL`, `CONTROLEUR`, `ADMIN`), `prenom`, `nom`, `telephone` |

La référence complète est dans les schémas `outils/personnaliser/schema/*.schema.json`. Chaque champ
y est décrit.

---

## 3. Où ranger les dossiers des vrais complexes : le dépôt privé `Clients_TerrainApp`

Les dépôts TerrainApp sont publics. **Seul `clients/exemple/` y est versionné.** Les dossiers des
vrais complexes contiennent leurs photos et les noms de leur personnel : ils vont dans le dépôt
**privé** `Clients_TerrainApp`.

```
Clients_TerrainApp/            (dépôt PRIVÉ)
├── README.md                  qui contacter chez chaque complexe, état des livraisons
├── .gitignore                 deploiements/  .env  CODES-ACTIVATION.txt  *.zip
└── clients/
    ├── arena-thies/
    │   ├── complexe.json
    │   ├── assets/
    │   └── donnees/
    └── five-mbour/
        └── …
```

Règles du dépôt privé :

- **Aucun secret**, ni dans le dépôt privé ni ailleurs dans git : pas de mot de passe, pas de clé de
  paiement, pas de `.env`, pas de code d'activation. Le schéma refuse d'ailleurs tout champ
  `motDePasse`.
- Une branche par livraison (`livraison/arena-thies`) et une PR relue avant chaque déploiement :
  la CI du dépôt privé peut lancer `npm run client -- clients/<id> --valider`.
- Clonez-le à côté des dépôts TerrainApp, puis donnez son chemin à la commande :

```bash
cd Backend_TerrainApp/outils/personnaliser
npm ci
npm run client -- ../../../Clients_TerrainApp/clients/arena-thies
```

Un chemin relatif part du dossier où vous lancez la commande. On peut aussi donner un chemin absolu.

---

## 4. Lancer la commande

Une fois par poste : `cd Backend_TerrainApp/outils/personnaliser && npm ci` (Node 20 ou plus).

```bash
npm run client -- <chemin-ou-identifiant> [options]
```

| Option | Effet |
|---|---|
| `--valider` | Valide seulement, ne produit rien. |
| `--sortie <dossier>` | Dossier du déploiement. Par défaut : `Backend_TerrainApp/deploiements/<identifiant>/`, ignoré par git. |
| `--profil prod\|demo` | `prod` par défaut. `demo` : paiement et SMS simulés, accès local autorisé (`http://localhost:5180`). |
| `--version x.y.z` | Version des images (par défaut `deploiement.version` du dossier, sinon `latest`). |
| `--images-locales` | Images construites sur ce poste (`terrainapp-*:local`), pour essayer sans registre. |

La commande :

1. **valide** le dossier : schéma, références entre fichiers, durées compatibles avec le pas,
   présence, format et dimensions des images. Toutes les erreurs sont listées en une fois, par
   exemple :

   ```
   ✗ complexe.json › marque.couleurs.principale : couleur invalide (format #RRGGBB, ex. #1F7A4D) (valeur : « vert »)
   ✗ donnees/terrains.json › [0].tarifHeure : montant invalide : nombre entier de F CFA, positif ou nul (valeur : « -5000 »)
   ✗ donnees/terrains.json › [0] (Terrain A).dureesProposees : durée 90 min incompatible avec le pas des créneaux (60 min)
   ✗ assets/logo.png : fichier absent (logo)
   ```

2. **produit** `deploiements/<identifiant>/` :
   - `complexe/` : copie du dossier, lue par le backend (identité, règles, données initiales) ;
   - `marque/` : logo, photos, icônes PWA et favicon (générés depuis le logo s'ils manquent),
     `marque.json` (identité publique lue par les deux apps) ;
   - `.env` : secrets tirés au hasard **la première fois puis conservés**, réglages et marque
     (variables documentées dans `outils/personnaliser/.env.exemple`) ;
   - `docker-compose.yml` : images versionnées, identiques pour tous les complexes ;
   - `CODES-ACTIVATION.txt` : un code à usage unique par nouveau compte du personnel.

La commande se relance sans risque. Les secrets et les codes déjà produits sont gardés ; seul un
compte ajouté au dossier reçoit un nouveau code.

### Comptes du personnel : codes d'activation

Aucun mot de passe n'est jamais écrit. Chaque compte reçoit un **code à usage unique** (ex.
`K7QX-M2PA-9RTE`), affiché une seule fois et écrit dans `CODES-ACTIVATION.txt`. Le backend n'en
garde que l'empreinte BCrypt, transmise par le `.env`.

1. Remettez chaque code **en main propre** à la personne concernée.
2. À sa première connexion au Manager, elle saisit son identifiant et son code, puis choisit son
   mot de passe (12 caractères minimum). Le code ne sert alors plus à rien.
3. **Supprimez `CODES-ACTIVATION.txt`** une fois les codes remis.

---

## 5. Vérifier avant de livrer

1. Démonstration sur votre poste :
   `npm run client -- <complexe> --profil demo --images-locales`, puis dans le dossier produit :
   `docker compose up -d --wait`. Les images locales se construisent depuis chaque dépôt avec
   `docker build -t terrainapp-backend:local .` (de même `terrainapp-client:local` et
   `terrainapp-manager:local`).
2. App des joueurs (`http://localhost:5180`) : nom et logo sur l'écran de démarrage et dans l'en-tête,
   couleur, image d'accueil, terrains et photos, moyens de paiement proposés, ticket QR et reçu PDF
   au nom du complexe, bouton « Itinéraire » vers la bonne position.
3. Manager (`http://localhost:5181`) : titre de l'onglet « <Nom> Manager », logo, couleur, première
   connexion avec un code d'activation, reçu de caisse.
4. API : `curl http://localhost:5180/api/v1/marque` renvoie `"personnalise": true` et l'identité
   du complexe.
5. Rejouer le chargement ne crée aucun doublon. Le backend écrit au démarrage
   `Donnees du complexe : 0 element(s) cree(s), N deja present(s)`.

---

## 6. Déployer chez le complexe

1. Copiez `deploiements/<identifiant>/` sur le serveur du complexe par un canal sûr (`scp`), jamais
   par messagerie.
2. Sur le serveur : `docker compose up -d --wait`. Placez un proxy HTTPS (Caddy, nginx) devant les
   ports `PORT_CLIENT` et `PORT_MANAGER`, aux adresses de `deploiement.urlClient` et `urlManager`.
3. Profil `prod` : le backend **refuse de démarrer** tant que le paiement et les SMS sont simulés.
   C'est voulu, en attendant le branchement de PayDunya et du prestataire SMS. Les clés de ces
   prestataires iront dans le `.env` du serveur, jamais dans `clients/`.
4. Sauvegardez le `.env` du serveur dans le coffre de SSD Consulting. Le perdre rendrait la base
   illisible (mot de passe MySQL) et déconnecterait tout le personnel (secret JWT).

### Mettre à jour

- **Nouvelle version de TerrainApp** : un tag `vX.Y.Z` sur chaque dépôt publie les images sur GHCR
  (`ghcr.io/malick2niang/*_terrainapp`). Ensuite, `npm run client -- <complexe> --version X.Y.Z`
  (ou `TERRAINAPP_VERSION` dans le `.env` du serveur), puis `docker compose pull && docker compose up -d`.
- **Changement de marque** (logo, couleurs, textes) : modifiez le dossier, relancez la commande,
  recopiez `complexe/`, `marque/` et `.env`, puis `docker compose up -d`.
- **Nouveau terrain ou nouveau compte** : ajoutez-le au dossier avec une nouvelle clé. Au
  redémarrage, seul ce qui est nouveau est créé. Ce que le gérant a modifié dans le Manager n'est
  jamais écrasé.

---

## Dépannage

| Symptôme | Cause et solution |
|---|---|
| Le backend ne démarre pas : `Configuration du complexe invalide` | Le dossier a été modifié à la main sur le serveur. Corrigez les erreurs listées, ou relancez la commande et recopiez `complexe/`. |
| `code d'activation absent de APP_COMPLEXE_ACTIVATIONS` | Un compte a été ajouté sans relancer la commande. Relancez-la et recopiez le `.env`. |
| `Configuration de production refusee : APP_PAIEMENT_PRESTATAIRE=simule` | Profil `prod` sans prestataires réels. Branchez-les, ou utilisez `--profil demo` pour une démonstration. |
| L'app garde l'ancienne marque | Le navigateur a mis la page en cache. Rechargez (`config.js` et `index.html` ne sont jamais mis en cache côté serveur). |
| Une photo n'apparaît pas | Nom du fichier différent de celui écrit dans `terrains.json` (majuscules, extension). La commande le signale. |
