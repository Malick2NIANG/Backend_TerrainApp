# Tests de charge (k6)

Ces scénarios mesurent la tenue de l'API TerrainApp sous charge et vérifient les garanties métier qui comptent le plus : **jamais deux réservations sur le même créneau**, **un double envoi ne réserve qu'une fois**.

Ils tournent sur une **pile de charge jetable** (`docker-compose.yml` de ce dossier) : MySQL en mémoire, backend construit depuis ce dépôt, port **8091**. Elle ne touche jamais la base de développement et se vide à chaque arrêt.

## Lancer (Windows, PowerShell)

```powershell
cd Backend_TerrainApp\load-tests
.\lancer.ps1 demarrer        # construit et démarre la pile (1 à 2 min la première fois)
.\lancer.ps1 tout            # les 5 scénarios à la suite (~10 min)
.\lancer.ps1 arreter         # supprime la pile et ses données
```

Un scénario seul : `.\lancer.ps1 smoke`. Sous Linux/macOS : `./lancer.sh` avec les mêmes arguments. Docker Desktop suffit : k6 tourne dans l'image `grafana/k6`.

Rapports : `resultats/<scénario>-<date>.html` (tableau de bord k6, à ouvrir dans le navigateur) et `.json` (résumé chiffré).

## Les scénarios

| Fichier | Ce qu'il vérifie | Durée |
|---|---|---|
| `smoke.js` | 1 joueur : accueil, fiche, créneaux, une réservation payée (simulée) et son suivi. | 30 s |
| `doublons.js` | La même réservation envoyée 5 fois en parallèle avec la même `Idempotency-Key` ne crée **qu'une** réservation ; un nouvel essai rejoue la même réponse ; la même clé avec un autre contenu renvoie 409 `IDEMPOTENCE_CONFLIT`. | ~5 s |
| `course-creneau.js` | 50 joueurs réservent **le même créneau** au même instant : exactement 1 succès (201) et 49 refus 409 `CRENEAU_PRIS`. `JOUEURS=100` pour durcir. | ~10 s |
| `reservations.js` | Soir de match : 5, puis 30, puis 60 joueurs réservent en parallèle (lecture des créneaux, paiement, suivi) pendant que le gérant consulte son planning. Les 409 `CRENEAU_PRIS` (créneau pris entre la lecture et l'envoi) sont comptés à part. | ~4 min |
| `lecture-catalogue.js` | Ouverture de l'App Client par paliers de 10, 50, 200 puis 500 joueurs : accueil (6 lectures), fiche terrain, créneaux du jour. | ~5 min |

Variables utiles : `PALIER=2m` (durée de chaque palier), `PALIERS=10,50,200` (lecture), `JOUEURS=100` (course).

## Seuils (le passage échoue s'ils ne sont pas tenus)

| Mesure | Seuil |
|---|---|
| `http_req_failed` | < 1 % |
| `checks` | > 99 % (100 % pour la course et les doublons) |
| p95 lecture du catalogue (`groupe:catalogue`) | < 300 ms |
| p95 disponibilités (`groupe:dispos`) | < 400 ms |
| p95 `POST /reservations` | < 800 ms |
| p95 planning du gérant | < 500 ms |
| course au créneau | exactement 1 réussite, `JOUEURS − 1` refus |

Les 409 attendus (`CRENEAU_PRIS`, `REQUETE_EN_COURS`, `IDEMPOTENCE_CONFLIT`) ne comptent pas comme des échecs.

## Règles de sécurité

- **Liste blanche d'hôtes** : `localhost`, `127.0.0.1`, `host.docker.internal`, ou un hôte ajouté dans `ALLOWED_HOSTS`. Toute autre cible arrête le script avant la première requête.
- **Jamais la production** : la pile de charge force le paiement et les SMS simulés.
- **Aucun secret dans le dépôt** : les mots de passe (MySQL, JWT, gérant) sont générés au premier `demarrer` dans `.env.charge`, ignoré par git, et jamais affichés.
- La **limitation de débit** est relevée sur la pile de charge (k6 envoie tout depuis une seule IP) ; elle est couverte par les tests d'intégration du backend.
