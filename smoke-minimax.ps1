# =============================================================================
# smoke-minimax.ps1 — Smoke test end-to-end del endpoint
#   POST /v1/lms/ai/quiz-questions
# usando MiniMax como provider LLM (BE-7c.1.1).
#
# Pre-requisitos:
#   1. El back está corriendo con MINIMAX_ENABLED=true y MINIMAX_API_KEY
#      (usá .\run-dev.ps1 para arrancar).
#   2. La DB tiene el tenant 'demo' (seed V5) + ai_settings con ai_enabled=true
#      (este script lo activa si no).
#   3. La DB tiene al menos un usuario con rol TENANT_ADMIN o TEACHER.
#
# Uso:
#   .\smoke-minimax.ps1
#   .\smoke-minimax.ps1 -BaseUrl "http://localhost:8080" -Topic "suma de fracciones"
# =============================================================================

param(
    [string]$BaseUrl  = "http://localhost:8080",
    [string]$Topic    = "suma de fracciones",
    [int]$Count       = 2,
    [string]$Email    = "",
    [string]$Password = ""
)

$ErrorActionPreference = "Stop"
$api = "$BaseUrl/api/v1"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  MiniMax E2E smoke test" -ForegroundColor Cyan
Write-Host "  base-url: $BaseUrl" -ForegroundColor Cyan
Write-Host "  topic:    $Topic" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# ---------------------------------------------------------------------------
# 0. Activar AI en TODOS los tenants activos (idempotente).
#    El seed V36 los crea con ai_enabled=false. El endpoint lo requiere true.
#    Si tu user está en un tenant distinto de 'demo', esto igual lo prende.
# ---------------------------------------------------------------------------
Write-Host "`n[1/5] Activando ai_enabled=true en todos los tenants..." -ForegroundColor Yellow
$env:PGPASSWORD = "3ianian3"
# tenant_ai_settings.tenant_id apunta a tenants.public_uuid (no a tenants.id).
$psqlCheck = & psql -h localhost -U postgres -d postgres -tA -c `
    "SELECT t.slug, s.ai_enabled FROM edushift.tenant_ai_settings s
     JOIN edushift.tenants t ON t.public_uuid = s.tenant_id
     WHERE t.deleted = false" 2>$null

if ($LASTEXITCODE -ne 0) {
    Write-Host "  [WARN] psql no se pudo conectar. Saltando UPDATE. Asumo que el tenant ya está OK." -ForegroundColor DarkYellow
} elseif ([string]::IsNullOrWhiteSpace($psqlCheck)) {
    # psql corrió OK pero devolvió 0 filas (p.ej. tabla vacía). No hay nada que actualizar.
    Write-Host "  [WARN] psql no devolvió filas (tabla tenant_ai_settings vacía?). Saltando UPDATE." -ForegroundColor DarkYellow
} else {
    $rows = $psqlCheck.Trim() -split "`n"
    foreach ($row in $rows) {
        $parts = $row.Trim() -split "\|"
        $slug = $parts[0]
        $flag = $parts[1]
        if ($flag -eq "f") {
            & psql -h localhost -U postgres -d postgres -c `
                "UPDATE edushift.tenant_ai_settings SET ai_enabled = true
                 WHERE tenant_id = (SELECT public_uuid FROM edushift.tenants WHERE slug='$slug')" 2>$null
            Write-Host "  [OK] tenant='$slug' → ai_enabled=true" -ForegroundColor Green
        } else {
            Write-Host "  [OK] tenant='$slug' → ai_enabled ya estaba en: $flag" -ForegroundColor Green
        }
    }
}

# ---------------------------------------------------------------------------
# 1. Verificar que el back está vivo.
#    Probamos en orden:
#      (a) /actuator/health  → 200 OK si está expuesto.
#      (b) /api/v1/auth/ping → 401 = back vivo (auth requerida).
#    Cualquier respuesta HTTP (incluso 4xx) cuenta como "back vivo".
#    Sólo "no se puede conectar" o timeout cuenta como "back muerto".
# ---------------------------------------------------------------------------
Write-Host "`n[2/5] Health check ($BaseUrl)..." -ForegroundColor Yellow
$alive = $false

function Test-BackAlive {
    param([string]$Url, [string]$Description)
    try {
        $null = Invoke-WebRequest -Method GET -Uri $Url -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
        Write-Host "  [OK] $Description responded (back vivo)" -ForegroundColor Green
        return $true
    } catch {
        $ex = $_.Exception
        # Conexión refused, DNS fail, timeout → "back muerto".
        if ($ex -is [System.Net.WebException] -and
            ($ex.Status -eq 'ConnectFailure' -or $ex.Status -eq 'NameResolutionFailure' -or $ex.Status -eq 'Timeout')) {
            return $false
        }
        # Cualquier otro error (4xx, 5xx con response) = back vivo.
        $code = $null
        if ($ex.Response) { $code = [int]$ex.Response.StatusCode }
        Write-Host "  [OK] $Description responded with HTTP $code (back vivo)" -ForegroundColor Green
        return $true
    }
}

# Probar /actuator/health primero.
$alive = Test-BackAlive -Url "$BaseUrl/actuator/health" -Description "/actuator/health"
if (-not $alive) {
    # Fallback a un endpoint autenticado.
    $alive = Test-BackAlive -Url "$api/auth/ping" -Description "/api/v1/auth/ping"
}

if (-not $alive) {
    Write-Host "  [ERROR] El back no responde en $BaseUrl" -ForegroundColor Red
    Write-Host "  Asegurate de haber arrancado con .\run-dev.ps1" -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------------------
# 2. Listar usuarios existentes para encontrar uno con TEACHER/TENANT_ADMIN.
# ---------------------------------------------------------------------------
Write-Host "`n[3/5] Buscando un usuario con rol TEACHER o TENANT_ADMIN..." -ForegroundColor Yellow
# EduShift guarda roles como array de varchar(40) directamente en users.roles.
# Usamos 'ADMIN' o 'TEACHER' (no TENANT_ADMIN en este schema).
$userRow = & psql -h localhost -U postgres -d postgres -tA -F "|" -c `
    "SELECT public_uuid, email, array_to_string(roles, ',')
     FROM edushift.users
     WHERE deleted = false
       AND status = 'ACTIVE'
       AND (roles @> ARRAY['TEACHER']::varchar[] OR roles @> ARRAY['ADMIN']::varchar[] OR roles @> ARRAY['TENANT_ADMIN']::varchar[])
     ORDER BY (roles @> ARRAY['ADMIN']::varchar[]) DESC
     LIMIT 1" 2>$null

if ($LASTEXITCODE -ne 0 -or -not $userRow) {
    Write-Host "  [WARN] No se encontraron usuarios con TEACHER/TENANT_ADMIN en la DB." -ForegroundColor DarkYellow
    Write-Host "  Probá crear uno vía el FE o pedime el script de seed." -ForegroundColor DarkYellow
    if (-not $Email -or -not $Password) {
        $email = Read-Host "  Ingresá el email de un user con TEACHER/TENANT_ADMIN"
        $password = Read-Host "  Ingresá su password" -AsSecureString
        $plainPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($password))
    } else {
        $email = $Email
        $plainPassword = $Password
    }
} else {
    $parts = $userRow.Split("|")
    $email = $parts[1]
    Write-Host "  [OK] Encontrado: email=$email (uuid=$($parts[0]), rol=$($parts[2]))" -ForegroundColor Green
    Write-Host "  Si no recordás el password, paralo con Ctrl+C y usá el flow de reset desde el FE." -ForegroundColor Gray
    # Override opcional: si pasaron -Email y -Password por param, los usamos
    # (útil para CI / scripts automatizados / debug).
    if (-not $Email -or -not $Password) {
        $password = Read-Host "  Ingresá el password de $email" -AsSecureString
        $plainPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($password))
    } else {
        $email = $Email
        $plainPassword = $Password
    }
}

# ---------------------------------------------------------------------------
# 3. Login → obtener JWT.
#    POST /auth/login requiere el header X-Tenant-Slug (cada user vive en
#    un tenant). Lo sacamos de la DB antes de pedir el password.
# ---------------------------------------------------------------------------
Write-Host "`n[4/5] Login en $api/auth/login..." -ForegroundColor Yellow

# Lookup del tenant_slug del user (es el header obligatorio del /login).
$tenantSlug = & psql -h localhost -U postgres -d postgres -tA -c `
    "SELECT t.slug FROM edushift.users u
     JOIN edushift.tenants t ON t.id = u.tenant_id
     WHERE u.email = '$email' AND u.deleted = false
     LIMIT 1" 2>$null
$tenantSlug = $tenantSlug.Trim()
if ($LASTEXITCODE -ne 0 -or -not $tenantSlug) {
    Write-Host "  [ERROR] No pude resolver el tenant_slug del user '$email'" -ForegroundColor Red
    exit 1
}
Write-Host "  [INFO] tenant_slug=$tenantSlug" -ForegroundColor Gray

try {
    $loginBody = @{
        email    = $email
        password = $plainPassword
    } | ConvertTo-Json

    $loginResp = Invoke-RestMethod -Method POST -Uri "$api/auth/login" `
        -ContentType "application/json" -Body $loginBody `
        -Headers @{ "X-Tenant-Slug" = $tenantSlug } `
        -TimeoutSec 10
    $jwt = $loginResp.accessToken
    if (-not $jwt) {
        throw "Respuesta sin accessToken: $loginResp"
    }
    Write-Host "  [OK] JWT obtained (len=$($jwt.Length))" -ForegroundColor Green
} catch {
    $statusCode = $null
    $errBody = $null
    if ($_.Exception.Response) { $statusCode = [int]$_.Exception.Response.StatusCode }
    if ($_.ErrorDetails) { $errBody = $_.ErrorDetails.Message }
    Write-Host "  [ERROR] Login falló (HTTP $statusCode)" -ForegroundColor Red
    if ($errBody) { Write-Host "  $errBody" -ForegroundColor Red }
    exit 1
}

# ---------------------------------------------------------------------------
# 4. POST /v1/lms/ai/quiz-questions.
# ---------------------------------------------------------------------------
Write-Host "`n[5/5] POST $api/lms/ai/quiz-questions (topic='$Topic', count=$Count)..." -ForegroundColor Yellow
$body = @{
    topic = $Topic
    count = $Count
} | ConvertTo-Json

$startMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
try {
    $resp = Invoke-RestMethod -Method POST -Uri "$api/lms/ai/quiz-questions" `
        -ContentType "application/json" -Body $body `
        -Headers @{ Authorization = "Bearer $jwt" } -TimeoutSec 60
    $elapsed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - $startMs
    Write-Host "  [OK] HTTP 200 in ${elapsed}ms" -ForegroundColor Green
    Write-Host ""
    Write-Host "  --- Response (pretty) ---" -ForegroundColor Cyan
    $resp | ConvertTo-Json -Depth 8 | Write-Host
} catch {
    $elapsed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - $startMs
    $statusCode = $_.Exception.Response.StatusCode.value__
    $errBody = $_.ErrorDetails.Message
    Write-Host "  [ERROR] HTTP $statusCode in ${elapsed}ms" -ForegroundColor Red
    Write-Host "  $errBody" -ForegroundColor Red

    if ($statusCode -eq 403) {
        Write-Host ""
        Write-Host "  Troubleshooting:" -ForegroundColor Yellow
        Write-Host "  - El user no tiene autoridad LMS_AI_GENERATE. Verificalo en la tabla user_roles / permissions." -ForegroundColor Yellow
        Write-Host "  - O el tenant tiene ai_enabled=false (paso 1)." -ForegroundColor Yellow
    } elseif ($statusCode -eq 429) {
        Write-Host ""
        Write-Host "  Troubleshooting:" -ForegroundColor Yellow
        Write-Host "  - El tenant excedió la cuota diaria/mensual. UPDATEá tenant_ai_settings o esperá al reset." -ForegroundColor Yellow
    } elseif ($statusCode -eq 502) {
        Write-Host ""
        Write-Host "  Troubleshooting:" -ForegroundColor Yellow
        Write-Host "  - MiniMax rechazó la request. Revisá los logs del back (tail -f logs/edushift.*.log)." -ForegroundColor Yellow
        Write-Host "  - Verificá MINIMAX_API_KEY sea válida en https://platform.minimax.io" -ForegroundColor Yellow
    }
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  ✅ SMOKE TEST EXITOSO" -ForegroundColor Green
Write-Host "  Si las preguntas tienen sentido y no son un placeholder de Mock," -ForegroundColor Green
Write-Host "  MiniMax está respondiendo correctamente." -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
