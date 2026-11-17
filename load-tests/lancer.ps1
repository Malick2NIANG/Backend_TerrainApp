# Tests de charge k6 (PowerShell). Aucune installation : k6 tourne dans Docker.
#
#   .\lancer.ps1 demarrer              # pile de charge jetable (port 8091)
#   .\lancer.ps1 smoke                 # puis un scenario
#   .\lancer.ps1 lecture-catalogue     # PALIER=2m, PALIERS=10,50,200 en variables
#   .\lancer.ps1 tout                  # smoke, doublons, course, reservations, lecture
#   .\lancer.ps1 arreter               # supprime la pile et ses donnees
#
# Secrets : generes au demarrage (aleatoires) et gardes dans .env.charge
# (ignore par git), jamais affiches. Rapports : resultats\<scenario>-<date>.html
# (tableau de bord k6) et .json (resume).
param([Parameter(Mandatory = $true)][string]$Scenario)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$fichierEnv = Join-Path $PSScriptRoot '.env.charge'
function Nouveau-Secret { -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 40 | ForEach-Object { [char]$_ }) }
if (-not (Test-Path $fichierEnv)) {
  @(
    "CHARGE_MYSQL_PASSWORD=$(Nouveau-Secret)",
    "CHARGE_MYSQL_ROOT_PASSWORD=$(Nouveau-Secret)",
    "CHARGE_JWT_SECRET=$(Nouveau-Secret)$(Nouveau-Secret)",
    "K6_MDP_GERANT=$(Nouveau-Secret)"
  ) | Set-Content -Encoding ascii $fichierEnv
}
Get-Content $fichierEnv | ForEach-Object { $k, $v = $_ -split '=', 2; Set-Item -Path "env:$k" -Value $v }

if ($Scenario -eq 'demarrer') {
  docker compose --env-file $fichierEnv up -d --build --wait
  Write-Host 'Pile de charge prete : http://localhost:8091/api/v1'
  exit 0
}
if ($Scenario -eq 'arreter') {
  docker compose --env-file $fichierEnv down -v
  exit 0
}

$scenarios = if ($Scenario -eq 'tout') { @('smoke', 'doublons', 'course-creneau', 'reservations', 'lecture-catalogue') } else { @($Scenario) }
New-Item -ItemType Directory -Force -Path resultats | Out-Null
$echecs = 0
foreach ($s in $scenarios) {
  if (-not (Test-Path "$s.js")) { throw "Scenario inconnu : $s.js" }
  $horodatage = Get-Date -Format 'yyyyMMdd-HHmmss'
  Write-Host "=== $s ==="
  docker run --rm -i --add-host host.docker.internal:host-gateway `
    -v "${PWD}:/scripts" -w /scripts `
    -e BASE_URL -e ALLOWED_HOSTS -e PALIER -e PALIERS -e JOUEURS -e K6_MDP_GERANT -e K6_ID_GERANT `
    -e K6_WEB_DASHBOARD=true -e "K6_WEB_DASHBOARD_EXPORT=resultats/$s-$horodatage.html" `
    grafana/k6:1.3.0 run --summary-export "resultats/$s-$horodatage.json" "$s.js"
  if ($LASTEXITCODE -ne 0) { $echecs++ }
}
exit $echecs
