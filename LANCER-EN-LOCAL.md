# Lancer TerrainApp en local (les 3 dépôts, vrai parcours)

Ce guide démarre les trois applications sur une seule machine, branchées sur le **vrai backend**, sans mock :

| Application | Rôle | Adresse locale |
|---|---|---|
| `Backend_TerrainApp` | API, base MySQL, règles métier | http://localhost:8090/api/v1 |
| `Client_TerrainApp` | App des joueurs : terrains, réservation, paiement simulé, ticket | http://localhost:5180 |
| `Manager_TerrainApp` | Back-office : Gérant et Admin (propriétaire du complexe) | http://localhost:5181 |

Le paiement est **simulé** : aucune somme réelle n'est débitée, et la réservation est confirmée dès le paiement.

Les ports sont différents de ceux de SenYummies (8080, 5173, 5174) : les deux projets peuvent tourner en même temps.

---

## 0. Prérequis (une seule fois)

- **Docker Desktop**, démarré.
- **Node.js 20** et npm.
- Les trois dépôts clonés côte à côte dans un même dossier `Repos/`.

---

## 1. Backend + base de données

```bash
cd Backend_TerrainApp
cp .env.example .env
```

Renseigne `.env` avec tes propres valeurs. Ce fichier n'est jamais commité.

| Variable | Valeur pour un test local |
|---|---|
| `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` | deux mots de passe au choix |
| `MYSQL_DATABASE`, `MYSQL_USER` | `terrainapp` / `terrainapp` |
| `JWT_SECRET` | une longue chaîne aléatoire (32 caractères minimum) |
| `SPRING_PROFILES_ACTIVE` | `dev` : charge les **terrains de démonstration** (3 sports, 5 terrains, des annonces) et Swagger |
| `APP_AUTH_SEED_ENABLED` | `true` : crée les comptes du personnel au démarrage |
| `APP_AUTH_SEED_GERANT_PASSWORD`, `…_ADMIN_PASSWORD` | un mot de passe par compte, à ton choix |
| `APP_AUTH_SEED_ACCUEIL_PASSWORD`, `…_CONTROLEUR_PASSWORD` | **obligatoires aussi**, même si ces rôles sont désactivés pour l'instant : sans eux, le backend refuse de démarrer |
| `APP_PAIEMENT_PRESTATAIRE` | `simule` |
| `APP_PAIEMENT_SIMULE_MODE` | `immediat` |
| `MARQUE_*` | l'identité du complexe affichée (nom, adresse, téléphone…) ; les valeurs d'exemple suffisent |

Puis :

```bash
docker compose up -d --build --wait
```

Pour vérifier :

- http://localhost:8090/api/v1/health doit répondre `UP` ;
- http://localhost:8090/api/v1/terrains doit renvoyer les terrains de démonstration ;
- la documentation de l'API est sur http://localhost:8090/api/v1/swagger-ui.html ;
- MySQL est accessible depuis la machine sur `127.0.0.1:3307`.

Le projet Docker s'appelle `terrainapp` : `docker compose up` suffit, sans `-p`.

### Comptes du personnel créés

| Identifiant | Rôle | Mot de passe |
|---|---|---|
| `gerant` | GÉRANT : planning, réservations au comptoir, scan des tickets, terrains et tarifs, annonces, abonnements, avis, historique (export Excel) | `APP_AUTH_SEED_GERANT_PASSWORD` |
| `admin` | ADMIN, le propriétaire du complexe : tableau de bord et planning du jour, équipe (comptes gérants), journal des actions | `APP_AUTH_SEED_ADMIN_PASSWORD` |

Les comptes `accueil` et `controleur` existent en base mais ne peuvent pas se connecter : ces rôles sont désactivés dans le back-office pour l'instant (le gérant couvre leurs fonctions).

---

## 2. App Client (terminal 2)

```bash
cd Client_TerrainApp
cp .env.example .env        # garde VITE_API_BASE_URL=/api/v1
npm install
npm run dev                 # http://localhost:5180
```

Le serveur Vite relaie `/api/v1` vers le backend (port 8090). Pas de mock : sans backend démarré, l'app affiche une erreur réseau.

---

## 3. Manager (terminal 3)

```bash
cd Manager_TerrainApp
cp .env.example .env        # garde VITE_API_BASE_URL=/api/v1
npm install
npm run dev                 # http://localhost:5181
```

Si un port est déjà pris, Vite choisit le suivant. Le terminal affiche toujours le port réellement utilisé.

---

## 4. Le vrai parcours, de bout en bout

Garde deux fenêtres ouvertes : l'App Client et le Manager (connecté en `gerant`).

1. **Client** (5180) : choisis un terrain, une date et un créneau libre. Indique ton prénom, ton nom et un numéro sénégalais, par exemple `77 123 45 67`, puis choisis Wave, Orange Money ou Mixx by Yas. Le paiement est simulé : la réservation passe directement **confirmée**, et le **ticket** (QR code) s'affiche.
2. **Manager › Planning** (`gerant`) : la réservation apparaît en temps réel, avec une notification. Le créneau est pris côté Client aussi.
3. **Manager › Scanner un ticket** (`gerant`) : scanne le QR code du ticket du joueur, ou saisis son code. L'entrée est **validée** ; un second scan du même ticket est refusé (« déjà utilisé »).
4. **Client** : après le match, le joueur peut **noter le terrain**. La note moyenne s'affiche dans **Mon complexe** et dans **Avis des joueurs**.
5. **Manager › Historique** (`gerant`) : retrouve les réservations passées et exporte-les en Excel.
6. **Manager** (`admin`) : le tableau de bord montre le planning du jour en temps réel ; le **Journal** liste toutes les actions du personnel.

À essayer aussi :

- **Réservation au comptoir** : depuis le Planning, le gérant réserve pour un joueur sur place, paiement en espèces.
- **Fermer un créneau** : le gérant bloque un terrain (entretien, événement) ; le créneau n'est plus réservable côté Client.
- **Abonnement** : une série de séances (même jour, même heure) pour un client régulier.
- **Mes réservations sans compte** : côté Client, retrouve tes réservations avec ton numéro de téléphone.
- **Double appui** : appuyer deux fois sur « Payer » ne crée qu'une réservation.

---

## 5. Tester depuis un téléphone (tunnels)

Dans VS Code, onglet **Ports** :

1. transfère les ports **5180** (Client) et **5181** (Manager) ;
2. clic droit, **Visibilité du port : Public**.

Ouvre ensuite les adresses `https://…-5180…devtunnels.ms` et `https://…-5181…devtunnels.ms` sur le téléphone. L'API passe par le même tunnel : **inutile d'exposer le port 8090**.

Pour scanner un ticket avec l'appareil photo, il faut HTTPS : c'est le cas des tunnels, pas d'une IP locale en `http://`.

---

## 6. Arrêter ou repartir de zéro

```bash
# Front : Ctrl+C dans chaque terminal

cd Backend_TerrainApp
docker compose down       # arrête, en gardant les données
docker compose down -v    # arrête et EFFACE la base, les photos et les vidéos (démo rechargée au prochain démarrage)
```

---

## En cas de souci

| Symptôme | Cause probable |
|---|---|
| Le backend s'arrête au démarrage avec `Missing password for seeded user …` | Un des quatre mots de passe `APP_AUTH_SEED_*_PASSWORD` est vide dans `.env`. |
| L'app affiche une erreur réseau, ou aucun terrain | Le backend n'est pas démarré ou pas encore prêt : vérifie `/health`. |
| Aucun terrain alors que le backend répond | Le profil n'est pas `dev` : les terrains de démonstration ne sont chargés qu'en `dev`. |
| Le Manager refuse la connexion | `APP_AUTH_SEED_ENABLED` n'était pas à `true` au **premier** démarrage. Fais `docker compose down -v`, puis relance. Les comptes `accueil` et `controleur` sont refusés par conception. |
| « On ne réserve pas aussi loin à l'avance » | On réserve au plus 60 jours à l'avance. |
| 429 « Trop de requêtes » | Limites par minute et par IP : 10 réservations, 10 connexions, 30 consultations de suivi. Attends une minute. |
| Une réservation non payée disparaît | Elle libère son créneau après 15 minutes sans paiement (`APP_PAIEMENT_DELAI_EXPIRATION`). |
| Pas de notification dans le Planning | Le temps réel passe par `/api/v1/temps-reel`. Recharge la page ; le Manager se reconnecte tout seul. |
| Le téléphone ne charge pas l'API | `VITE_API_BASE_URL` doit valoir `/api/v1`, pas `http://localhost:8090/...`. |

Tests de charge (k6) : voir [`load-tests/README.md`](load-tests/README.md).
