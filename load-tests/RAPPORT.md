# Rapport des tests de charge — 17 novembre 2026

Pile de charge jetable (`docker-compose.yml`) sur un poste de développement Windows (Docker Desktop) : backend Spring Boot en profil `dev` (terrains de démonstration), MySQL 8 en mémoire, paiement et SMS simulés. k6 1.3.0 en conteneur.

## Verdict

**Tous les seuils sont tenus.** Les deux garanties métier sont vérifiées sous concurrence : un créneau n'est jamais réservé deux fois, et un double envoi ne crée qu'une seule réservation. Les temps de réponse restent très en dessous des seuils, y compris à 500 joueurs simultanés.

## Résultats

| Scénario | Charge | Requêtes | Échecs HTTP | Résultat clé | Seuils |
|---|---|---|---|---|---|
| `smoke` | 1 joueur, 30 s | 141 | 0 % | 14 parcours complets (accueil → réservation payée → suivi) ; p95 60 ms | ✅ |
| `doublons` | 5 envois parallèles, même `Idempotency-Key` | 9 | 0 % | 1 seule réservation ; nouvel essai rejoué (`Idempotency-Replayed: true`) ; même clé + autre contenu → 409 `IDEMPOTENCE_CONFLIT` | ✅ 6/6 vérifications |
| `course-creneau` | 50 joueurs, même créneau, même instant | 53 | 0 % | **1 réussite (201), 49 refus (409 `CRENEAU_PRIS`)** ; créneau final `RESERVE` ; p95 227 ms | ✅ |
| `reservations` | 5 → 30 → 60 joueurs qui réservent + 2 gérants sur le planning, ~4 min | 4 282 | 0 % | 1 346 réservations créées ; 6 créneaux pris entre la lecture et l'envoi (409 attendu) | ✅ |
| `lecture-catalogue` | 10 → 50 → 200 → 500 joueurs, ~5 min | 82 600 | 0 % | 288 req/s soutenues, 100 % des vérifications | ✅ |

### Temps de réponse (p95)

| Mesure | Seuil | Mesuré |
|---|---|---|
| Catalogue (accueil, fiche terrain) à 500 joueurs | < 300 ms | **26 ms** |
| Disponibilités à 500 joueurs | < 400 ms | **34 ms** |
| Disponibilités pendant les réservations | < 400 ms | **19 ms** |
| `POST /reservations` à 60 joueurs | < 800 ms | **32 ms** |
| Planning du gérant sous charge | < 500 ms | **17 ms** |
| `POST /reservations`, 50 sur le même créneau | — | 227 ms (verrou sur le créneau, attendu) |

Le parcours complet d'un joueur qui réserve (créneaux, réservation, suivi, temps de réflexion compris) dure 3 s en médiane.

## Points relevés pendant la campagne

1. **Relais de ports de Docker Desktop.** Lancé contre `host.docker.internal:8091`, le palier de 500 joueurs perdait 7,7 % des connexions (`EOF`, `dial: i/o timeout`) sans aucune erreur HTTP ni ralentissement du backend (p95 61 ms) : c'est le relais réseau de Docker Desktop qui saturait. k6 rejoint désormais le réseau de la pile et appelle `http://backend:8080` : 0 % d'échec. En production (sans Docker Desktop), ce relais n'existe pas.
2. **Comptes du seed.** Le backend refuse de démarrer si l'un des quatre comptes seedés n'a pas de mot de passe ; la pile de charge les génère tous.
3. **Période réservable.** On ne réserve pas au-delà de 60 jours (`DATE_HORS_PERIODE`). Les scénarios course et doublons utilisaient J+75 à J+105 et n'avaient rien vérifié au premier passage ; ils sont maintenant à J+55–59 et J+50–54.

Aucun défaut de l'application n'a été trouvé.

## Limites

- Poste de développement, pas un serveur de production : les chiffres donnent un ordre de grandeur et une non-régression, pas une capacité garantie.
- La limitation de débit est relevée sur la pile de charge (k6 envoie tout depuis une seule IP) ; elle est couverte par les tests d'intégration.
- Les réservations dépendent du paiement simulé « immédiat » ; le vrai prestataire (PayDunya) ajoutera sa propre latence.

## Rejouer

```powershell
.\lancer.ps1 demarrer
.\lancer.ps1 tout
.\lancer.ps1 arreter
```
