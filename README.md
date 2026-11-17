# Backend_TerrainApp

API de **TerrainApp** : réservation de terrains de sport en marque blanche.
Une installation = un complexe sportif (sa base, sa marque).
Consommée par `Client_TerrainApp` (PWA des joueurs, sans compte) et
`Manager_TerrainApp` (back-office du personnel).

Stack : Spring Boot 4.1, Java 17, MySQL 8, Flyway, JWT (personnel uniquement),
SSE pour le temps réel, tests d'intégration avec Testcontainers.

## Lancer en local

Pour lancer les trois applications ensemble (backend, App Client, Manager) et faire le vrai parcours : [`LANCER-EN-LOCAL.md`](LANCER-EN-LOCAL.md).

```bash
cp .env.example .env      # puis remplir les mots de passe
docker compose up -d --build --wait
```

- API : http://localhost:8090/api/v1 (santé : `/health`)
- Documentation : http://localhost:8090/api/v1/swagger-ui.html
- MySQL : `127.0.0.1:3307` (ports différents de SenYummies : les deux tournent ensemble)
- Profil `dev` : 3 sports et 5 terrains de démonstration (fictifs).

Comptes du personnel créés au démarrage (`APP_AUTH_SEED_ENABLED=true`) :
`gerant`, `accueil`, `controleur`, `admin`, mots de passe dans `.env`.

## Tests

```bash
docker run --rm -it -v "${PWD}:/app" -v "$env:USERPROFILE\.m2:/root/.m2" \
  -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /app maven:3.9-eclipse-temurin-17 mvn verify
```

## Modules

| Paquet | Rôle |
|---|---|
| `terrains` | sports, terrains, plages tarifaires, horaires d'ouverture, blocages, calcul des créneaux |
| `reservations` | réservation (App / comptoir), suivi par jeton, historique par numéro, planning, scan des tickets |
| `paiements` | Wave / Orange Money / Mixx by Yas via `PaymentProvider` (simulé en attendant PayDunya), webhook signé, expiration |
| `marque` | identité du complexe (`MARQUE_*`) servie sur `GET /marque` |
| `tempsreel` | SSE : planning du personnel (`/temps-reel/planning`) et changements publics (`/temps-reel/public`) |
| `auth`, `audit` | comptes du personnel (Gérant, Accueil, Contrôleur, Admin) et journal des actions |

## Règles

- Montants en F CFA entiers ; heures stockées en UTC, Dakar = UTC+0.
- Jamais de double réservation : verrou sur le terrain dans la transaction.
- Une réservation en ligne tient son créneau le temps du paiement
  (`APP_PAIEMENT_DELAI_EXPIRATION`, 15 min par défaut), puis le libère.
- `Idempotency-Key` obligatoire sur `POST /reservations` : un double appui ne réserve pas deux fois.
- Migrations Flyway en ajout seulement. Données de test fictives. `.env` jamais commité.
