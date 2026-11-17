#!/usr/bin/env bash
# Tests de charge k6 (bash). Voir lancer.ps1 pour PowerShell.
#
#   ./lancer.sh demarrer | smoke | lecture-catalogue | reservations | course-creneau | doublons | tout | arreter
set -euo pipefail
cd "$(dirname "$0")"

fichier_env=.env.charge
secret() { LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 40; }
if [ ! -f "$fichier_env" ]; then
  {
    echo "CHARGE_MYSQL_PASSWORD=$(secret)"
    echo "CHARGE_MYSQL_ROOT_PASSWORD=$(secret)"
    echo "CHARGE_JWT_SECRET=$(secret)$(secret)"
    echo "K6_MDP_GERANT=$(secret)"
  } > "$fichier_env"
  chmod 600 "$fichier_env"
fi
set -a; . "./$fichier_env"; set +a

scenario="${1:?Usage : ./lancer.sh <demarrer|scenario|tout|arreter>}"
case "$scenario" in
  demarrer) docker compose --env-file "$fichier_env" up -d --build --wait; echo "Pile de charge prete : http://localhost:8091/api/v1"; exit 0 ;;
  arreter) docker compose --env-file "$fichier_env" down -v; exit 0 ;;
  tout) scenarios=(smoke doublons course-creneau reservations lecture-catalogue) ;;
  *) scenarios=("$scenario") ;;
esac

mkdir -p resultats
echecs=0
for s in "${scenarios[@]}"; do
  [ -f "$s.js" ] || { echo "Scenario inconnu : $s.js" >&2; exit 2; }
  curl -fsS -m 5 http://localhost:8091/api/v1/health >/dev/null 2>&1 || {
    echo "Le backend de charge ne repond pas sur le port 8091 (arret ou plantage)." >&2
    echo "  docker compose -p terrainapp-charge ps -a ; docker compose -p terrainapp-charge logs --tail 80 backend" >&2
    exit 1; }
  horodatage="$(date +%Y%m%d-%H%M%S)"
  echo "=== $s ==="
  MSYS_NO_PATHCONV=1 docker run --rm -i --add-host host.docker.internal:host-gateway \
    -v "$PWD:/scripts" -w /scripts \
    -e BASE_URL -e ALLOWED_HOSTS -e PALIER -e PALIERS -e JOUEURS -e K6_MDP_GERANT -e K6_ID_GERANT \
    -e K6_WEB_DASHBOARD=true -e K6_WEB_DASHBOARD_EXPORT="resultats/$s-$horodatage.html" \
    grafana/k6:1.3.0 run --summary-export "resultats/$s-$horodatage.json" "$s.js" || echecs=$((echecs + 1))
done
exit "$echecs"
