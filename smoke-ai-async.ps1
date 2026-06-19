# =============================================================================
# smoke-ai-async.ps1 — Smoke test end-to-end del flujo async (BE-7c.2):
#   POST /v1/lms/ai/quiz-questions?async=true  →  202 + generationUuid
#   GET  /v1/lms/ai/generations/{uuid}          →  poll hasta COMPLETED
#   DELETE /v1/lms/ai/generations/{uuid}        →  204 (cancel smoke)
#
# Pre-requisitos (idénticos a smoke-minimax.ps1):
#   1. El back está corriendo (usá .\run-dev.ps1).
#   2. Hay al menos un user con rol TENANT_ADMIN (este script lo busca en DB).
#   3. El tenant del user tiene ai_enabled=true (este script lo activa).
#
# Uso:
#   .\smoke-ai-async.ps1
#   .\smoke-ai-async.ps1 -BaseUrl "http://localhost:8080" -Topic "poesia" -Count 3
#   .\smoke-ai-async.ps1 -PollTimeoutSec 30
# =============================================================================

param(
    [string]$BaseUrl       = "http://localhost:8080",
    [string]$Topic         = "capitales de europa",
    [int]$Count            = 2,
    [int]$PollIntervalMs   = 1000,
    [int]$PollTimeoutSec   = 30,
    [string]$Email         = "",
    [string]$Password      = ""
)

$ErrorActionPreference = "Stop"
$api = "$BaseUrl/api/v1"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  AI async E2E smoke test (BE-7c.2)" -ForegroundColor Cyan
Write-Host "  base-url: $BaseUrl" -ForegroundColor Cyan
Write-Host "  topic:    $Topic (count=$Count)" -ForegroundColor Cyan
Write-Host "  poll:     ${PollIntervalMs}ms / max ${PollTimeoutSec}s" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# ---------------------------------------------------------------------------
# 0. Activar AI en todos los tenants (idempotente).
#    Reutilizamos el mismo SQL que smoke-minimax.ps1.
# ---------------------------------------------------------------------------
Write-Host "`n[1/6] Activando ai_enabled=true en todos los tenants..." -ForegroundColor Yellow
$env:PGPASSWORD = "3ianian3"
$psqlCheck = & psql -h localhost -U postgres -d postgres -tA -c `
    "SELECT t.slug, s.ai_enabled FROM edushift.tenant_ai_settings s
     JOIN edushift.tenants t ON t.public_uuid = s.tenant_id
     WHERE t.deleted = false" 2>$null

if ($LASTEXITCODE -ne 0) {
    Write-Host "  [WARN] psql no se pudo conectar. Asumo que el tenant ya está OK." -ForegroundColor DarkYellow
} elseif ([string]::IsNullOrWhiteSpace($psqlCheck)) {
    Write-Host "  [WARN] psql no devolvió filas (tabla vacía?). Saltando UPDATE." -ForegroundColor DarkYellow
} else {
    foreach ($row in ($psqlCheck.Trim() -split "`n")) {
        $parts = $row.Trim() -split "\|"
        $slug = $parts[0]
        $flag = $parts[1]
        if ($flag -eq "f") {
            & psql -h localhost -U postgres -d postgres -c `
                "UPDATE edushift.tenant_ai_settings SET ai_enabled = true
                 WHERE tenant_id = (SELECT public_uuid FROM edushift.tenants WHERE slug='$slug')" 2>$null | Out-Null
            Write-Host "  [OK] tenant='$slug' → ai_enabled=true" -ForegroundColor Green
        } else {
            Write-Host "  [OK] tenant='$slug' → ai_enabled=$flag (ya estaba)" -ForegroundColor Green
        }
    }
}

# ---------------------------------------------------------------------------
# 1. Health check (back vivo).
# ---------------------------------------------------------------------------
Write-Host "`n[2/6] Health check ($BaseUrl)..." -ForegroundColor Yellow
$alive = $false
function Test-BackAlive {
    param([string]$Url, [string]$Description)
    try {
        $null = Invoke-WebRequest -Method GET -Uri $Url -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
        Write-Host "  [OK] $Description responded (back vivo)" -ForegroundColor Green
        return $true
    } catch {
        $ex = $_.Exception
        if ($ex -is [System.Net.WebException] -and
            ($ex.Status -eq 'ConnectFailure' -or $ex.Status -eq 'NameResolutionFailure' -or $ex.Status -eq 'Timeout')) {
            return $false
        }
        $code = $null
        if ($ex.Response) { $code = [int]$ex.Response.StatusCode }
        Write-Host "  [OK] $Description responded with HTTP $code (back vivo)" -ForegroundColor Green
        return $true
    }
}

if (-not (Test-BackAlive "$BaseUrl/actuator/health" "/actuator/health")) {
    if (-not (Test-BackAlive "$api/auth/ping" "/api/v1/auth/ping")) {
        Write-Host "  [ERROR] El back no responde en $BaseUrl. Arrancá con .\run-dev.ps1" -ForegroundColor Red
        exit 1
    }
}

# ---------------------------------------------------------------------------
# 2. Buscar un user con rol TENANT_ADMIN o TEACHER.
# ---------------------------------------------------------------------------
Write-Host "`n[3/6] Buscando un user con rol TENANT_ADMIN o TEACHER..." -ForegroundColor Yellow
$psqlUser = & psql -h localhost -U postgres -d postgres -tA -F "|" -c `
    "SELECT u.email, u.public_uuid, t.slug
     FROM edushift.users u
     LEFT JOIN edushift.tenants t ON t.public_uuid = u.tenant_id
     WHERE u.status = 'ACTIVE'
       AND (u.roles @> ARRAY['TENANT_ADMIN']::varchar[])
     ORDER BY u.created_at DESC
     LIMIT 1" 2>$null

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($psqlUser)) {
    Write-Host "  [ERROR] No encontré un user TENANT_ADMIN/TEACHER. Creá uno o usá el seed." -ForegroundColor Red
    exit 1
}

$userParts = $psqlUser.Trim() -split "\|"
$userEmail = $userParts[0]
$userUuid  = $userParts[1]
$tenantSlug = $userParts[2]
Write-Host "  [OK] email=$userEmail, tenant_slug=$tenantSlug" -ForegroundColor Green

# ---------------------------------------------------------------------------
# 3. Login.
# ---------------------------------------------------------------------------
Write-Host "`n[4/6] Login en $api/auth/login..." -ForegroundColor Yellow
# Override opcional: si pasaron -Email y -Password por param, los usamos
# (útil para CI / scripts automatizados / debug). Si no, prompt interactivo.
if (-not $Email -or -not $Password) {
    $Email = $userEmail
    $Password = Read-Host "  Ingresá el password de $userEmail" -AsSecureString
    $plainPwd = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password))
} else {
    $userEmail = $Email
    $plainPwd = $Password
}

$loginBody = @{
    email    = $userEmail
    password = $plainPwd
} | ConvertTo-Json -Compress

try {
    $loginResp = Invoke-RestMethod -Method POST `
        -Uri "$api/auth/login" `
        -Headers @{ "X-Tenant-Slug" = $tenantSlug; "Content-Type" = "application/json" } `
        -Body $loginBody -TimeoutSec 10
    $jwt = $loginResp.data.accessToken
    if (-not $jwt) {
        Write-Host "  [ERROR] Login OK pero sin accessToken. Respuesta:" -ForegroundColor Red
        $loginResp | ConvertTo-Json -Depth 5 | Write-Host
        exit 1
    }
    Write-Host "  [OK] JWT obtained (len=$($jwt.Length))" -ForegroundColor Green
} catch {
    Write-Host "  [ERROR] Login falló: $_" -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------------------
# 4. POST /v1/lms/ai/quiz-questions?async=true → 202.
# ---------------------------------------------------------------------------
Write-Host "`n[5/6] POST $api/lms/ai/quiz-questions?async=true (topic='$Topic', count=$Count)..." -ForegroundColor Yellow
$aiBody = @{
    topic = $Topic
    count = $Count
} | ConvertTo-Json -Compress

try {
    $aiResp = Invoke-RestMethod -Method POST `
        -Uri "$api/lms/ai/quiz-questions?async=true" `
        -Headers @{ "Authorization" = "Bearer $jwt"; "X-Tenant-Slug" = $tenantSlug; "Content-Type" = "application/json" } `
        -Body $aiBody -TimeoutSec 10
} catch {
    $code = $null
    if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    Write-Host "  [ERROR] HTTP $code en el POST async: $_" -ForegroundColor Red
    exit 1
}

$generationUuid = $aiResp.data.generationUuid
$pollUrl = $aiResp.data.pollUrl
Write-Host "  [OK] 202 Accepted, generationUuid=$generationUuid" -ForegroundColor Green
Write-Host "  [OK] pollUrl=$pollUrl" -ForegroundColor Green

# ---------------------------------------------------------------------------
# 5. Poll GET /v1/lms/ai/generations/{uuid} hasta COMPLETED / FAILED.
# ---------------------------------------------------------------------------
Write-Host "`n[6/6] Polling $pollUrl cada ${PollIntervalMs}ms (max ${PollTimeoutSec}s)..." -ForegroundColor Yellow
$startTime = Get-Date
$finalStatus = $null
$finalBody   = $null
$attempts    = 0

while ($true) {
    $attempts++
    $elapsed = (Get-Date) - $startTime
    if ($elapsed.TotalSeconds -gt $PollTimeoutSec) {
        Write-Host "  [ERROR] Timeout tras $attempts polls (${elapsed.TotalSeconds}s). Status=$finalStatus" -ForegroundColor Red
        Write-Host "  Sugerencia: subí -PollTimeoutSec o revisá los logs del back." -ForegroundColor DarkYellow
        exit 1
    }
    try {
        $status = Invoke-RestMethod -Method GET `
            -Uri "$api$pollUrl" `
            -Headers @{ "Authorization" = "Bearer $jwt"; "X-Tenant-Slug" = $tenantSlug } `
            -TimeoutSec 5
        $finalStatus = $status.data.status
    } catch {
        $code = $null
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        Write-Host "  [ERROR] poll #$attempts HTTP $code : $_" -ForegroundColor Red
        exit 1
    }
    Write-Host "  poll #$attempts (${elapsed.TotalSeconds}s): $finalStatus" -ForegroundColor Gray

    if ($finalStatus -in @("COMPLETED", "FAILED", "CANCELLED")) {
        $finalBody = $status.data
        break
    }
    Start-Sleep -Milliseconds $PollIntervalMs
}

# ---------------------------------------------------------------------------
# 6. Resultado.
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
if ($finalStatus -eq "COMPLETED") {
    Write-Host "  ✅ ASYNC HAPPY PATH OK" -ForegroundColor Green
    Write-Host "  attempts:   $attempts (en $($(Get-Date) - $startTime).TotalSeconds s)" -ForegroundColor Green
    Write-Host "  model:      $($finalBody.model)" -ForegroundColor Green
    Write-Host "  provider:   $($finalBody.provider)" -ForegroundColor Green
    Write-Host "  promptVer:  $($finalBody.promptVersion)" -ForegroundColor Green
    Write-Host "  questions:  $($finalBody.questions.Count)" -ForegroundColor Green
    Write-Host "  latencyMs:  $($finalBody.latencyMs)" -ForegroundColor Green
    if ($finalBody.questions.Count -gt 0) {
        $q0 = $finalBody.questions[0]
        Write-Host "  q[0].type:  $($q0.type)" -ForegroundColor Green
        Write-Host "  q[0].prom:  $($q0.prompt.Substring(0, [Math]::Min(60, $q0.prompt.Length)))..." -ForegroundColor Green
    }
} elseif ($finalStatus -eq "FAILED") {
    Write-Host "  ❌ Generation FAILED" -ForegroundColor Red
    Write-Host "  errorCode:    $($finalBody.errorCode)" -ForegroundColor Red
    Write-Host "  errorMessage: $($finalBody.errorMessage)" -ForegroundColor Red
    Write-Host "  Esto puede pasar si el provider LLM no responde o devuelve JSON inválido." -ForegroundColor DarkYellow
    exit 1
} else {
    Write-Host "  ⚠ Status terminal: $finalStatus" -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# 7. Cancel smoke (opcional, no falla el script).
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "[bonus] Probando DELETE sobre una nueva generation (cancel smoke)..." -ForegroundColor Yellow
$cancelBody = @{
    topic = $Topic
    count = 1
} | ConvertTo-Json -Compress
try {
    $cancelResp = Invoke-RestMethod -Method POST `
        -Uri "$api/lms/ai/quiz-questions?async=true" `
        -Headers @{ "Authorization" = "Bearer $jwt"; "X-Tenant-Slug" = $tenantSlug; "Content-Type" = "application/json" } `
        -Body $cancelBody -TimeoutSec 10
    $cancelUuid = $cancelResp.data.generationUuid
    Write-Host "  [OK] created cancelable uuid=$cancelUuid" -ForegroundColor Green
    Start-Sleep -Milliseconds 50
    $null = Invoke-RestMethod -Method DELETE `
        -Uri "$api/lms/ai/generations/$cancelUuid" `
        -Headers @{ "Authorization" = "Bearer $jwt"; "X-Tenant-Slug" = $tenantSlug } `
        -TimeoutSec 5
    Write-Host "  [OK] DELETE returned 204" -ForegroundColor Green

    $postCancel = Invoke-RestMethod -Method GET `
        -Uri "$api/lms/ai/generations/$cancelUuid" `
        -Headers @{ "Authorization" = "Bearer $jwt"; "X-Tenant-Slug" = $tenantSlug } `
        -TimeoutSec 5
    Write-Host "  [OK] post-DELETE status: $($postCancel.data.status)" -ForegroundColor Green
} catch {
    Write-Host "  [WARN] cancel smoke falló (no crítico): $_" -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  AI async smoke done." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
