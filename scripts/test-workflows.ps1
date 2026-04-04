# Test manuel des workflows API (Correction, Mise en place, Utilisation)
# Prérequis : backend sur http://localhost:8080 (MySQL + seed DataInitializer)
# Usage : pwsh -File scripts/test-workflows.ps1

$ErrorActionPreference = "Stop"
$Base = "http://localhost:8080/api"

function Invoke-ApiGet($path, $token) {
    $h = @{ Authorization = "Bearer $token" }
    return Invoke-RestMethod -Uri "$Base$path" -Headers $h -Method Get
}

Write-Host "=== Login entreprise ===" -ForegroundColor Cyan
$loginBody = @{ username = "entreprise"; password = "123456" } | ConvertTo-Json
$loginResp = Invoke-RestMethod -Uri "$Base/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$tok = $loginResp.token
$eid = $loginResp.entrepriseId
if (-not $tok) { throw "Login échoué" }
if (-not $eid) { throw "entrepriseId manquant dans la réponse login" }

Write-Host "=== GET /demandes-correction/by-entreprise/$eid ===" -ForegroundColor Cyan
$dc = Invoke-ApiGet "/demandes-correction/by-entreprise/$eid" $tok
Write-Host ("Nombre de demandes: " + @($dc).Count)

Write-Host "=== GET /certificats-credit/by-entreprise/$eid ===" -ForegroundColor Cyan
$cc = Invoke-ApiGet "/certificats-credit/by-entreprise/$eid" $tok
$open = @($cc | Where-Object { $_.numero -eq "CI-TEST-OUVERT" })
if ($open.Count -eq 0) { Write-Warning "Certificat CI-TEST-OUVERT non trouvé (vérifiez le seed)" }
else { Write-Host "CI-TEST-OUVERT trouvé (statut: $($open[0].statut))" }

Write-Host "=== GET /utilisations-credit ===" -ForegroundColor Cyan
$u = Invoke-ApiGet "/utilisations-credit" $tok
Write-Host ("Nombre d'utilisations: " + @($u).Count)

Write-Host "`nOK — Workflows accessibles en lecture pour 'entreprise'." -ForegroundColor Green
Write-Host "Frontend : npm run dev dans cs-front-for-cursor/commission-fiscale-bd18374b, .env VITE_API_BASE=http://localhost:8080/api" -ForegroundColor DarkGray
