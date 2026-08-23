# =============================================================================
# run-dev.ps1 - Arranca el backend de EduShift cargando primero el .env
# (PowerShell no lee .env automaticamente; este script lo parchea).
#
# Uso:
#   .\run-dev.ps1
#   .\run-dev.ps1 -SkipMvn
#
# DB: siempre Postgres LOCAL (localhost:5432), igual que:
#   $env:SPRING_PROFILES_ACTIVE='dev'; .\mvnw.cmd spring-boot:run
# SMTP/Redis/MiniMax: vienen de .env (sin sobrescribir DB_*).
# =============================================================================

param(
    [switch]$SkipMvn = $false
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# ---------------------------------------------------------------------------
# 0. Forzar Postgres LOCAL (application-dev.properties defaults).
#    Ignora DB_* del .env (ahi suele estar Docker :5433).
# ---------------------------------------------------------------------------
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "edushift"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "3ianian3"
$env:DB_SCHEMA = "edushift"
$env:DB_SSL_MODE = "disable"
Remove-Item Env:DB_HOST_PORT -ErrorAction SilentlyContinue
Remove-Item Env:DB_APPLICATION_NAME -ErrorAction SilentlyContinue

# ---------------------------------------------------------------------------
# 1. Cargar .env (SMTP, Redis, MiniMax, etc.). DB_* se ignoran a proposito.
# ---------------------------------------------------------------------------
$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) {
    Write-Host "[run-dev] .env no encontrado en $envFile" -ForegroundColor Red
    exit 1
}

Write-Host "[run-dev] Cargando .env (SMTP/Redis/MiniMax; DB = local fijo)..." -ForegroundColor Cyan
$loaded = 0
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    if ($line -notmatch "=") { return }
    $name, $value = $line.Split("=", 2)
    $name = $name.Trim()
    $value = $value.Trim()
    if ($value.StartsWith('"') -and $value.EndsWith('"')) { $value = $value.Substring(1, $value.Length - 2) }
    if ($value.StartsWith("'") -and $value.EndsWith("'")) { $value = $value.Substring(1, $value.Length - 2) }
    if ($value -match '^\{' -or $value.Length -gt 500) { return }
    if ($name -match '^DB_') { return }
    Set-Item -Path "Env:$name" -Value $value
    $script:loaded++
}
Write-Host "[run-dev] Variables cargadas: $loaded" -ForegroundColor Gray

# Re-aplicar DB local por si .env u otra cosa la toco (defensa en profundidad).
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "edushift"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "3ianian3"
$env:DB_SCHEMA = "edushift"
$env:DB_SSL_MODE = "disable"

# ---------------------------------------------------------------------------
# 2. Validar MiniMax
# ---------------------------------------------------------------------------
if ($env:MINIMAX_ENABLED -eq "true") {
    Write-Host "[run-dev] MiniMax: ENABLED" -ForegroundColor Green
    Write-Host "[run-dev]   base-url: $env:MINIMAX_BASE_URL" -ForegroundColor Gray
    Write-Host "[run-dev]   model:    $env:MINIMAX_DEFAULT_MODEL" -ForegroundColor Gray
    if ($env:MINIMAX_API_KEY) {
        $keyPreview = $env:MINIMAX_API_KEY.Substring(0, [Math]::Min(20, $env:MINIMAX_API_KEY.Length))
        Write-Host "[run-dev]   api-key:  ${keyPreview}..." -ForegroundColor Gray
    }
} else {
    Write-Host "[run-dev] MiniMax: DISABLED (MockLlmClient activo)" -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# 2b. Validar SMTP
# ---------------------------------------------------------------------------
if ($env:APP_NOTIFICATIONS_EMAIL_ENABLED -eq "true" -and $env:SPRING_MAIL_HOST) {
    Write-Host "[run-dev] SMTP: ENABLED ($($env:SPRING_MAIL_HOST):$($env:SPRING_MAIL_PORT))" -ForegroundColor Green
    Write-Host "[run-dev]   from: $($env:APP_NOTIFICATIONS_EMAIL_FROM)" -ForegroundColor Gray
} else {
    Write-Host '[run-dev] SMTP: DISABLED - no se enviaran correos de reset ni invitaciones' -ForegroundColor Yellow
    Write-Host '[run-dev]   Tip: APP_NOTIFICATIONS_EMAIL_ENABLED=true y SPRING_MAIL_HOST en .env' -ForegroundColor Gray
}

# ---------------------------------------------------------------------------
# 2c. Preflight: Postgres LOCAL
# ---------------------------------------------------------------------------
Write-Host "[run-dev] DB local: jdbc:postgresql://localhost:5432/edushift (user=postgres, schema=edushift)" -ForegroundColor Cyan
$pgOk = Test-NetConnection -ComputerName "localhost" -Port 5432 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
if (-not $pgOk.TcpTestSucceeded) {
    Write-Host "[run-dev] PostgreSQL LOCAL NO disponible en localhost:5432" -ForegroundColor Red
    Write-Host '[run-dev]   Arranca tu Postgres local (mismo que usas con mvnw spring-boot:run).' -ForegroundColor Yellow
    exit 1
}
Write-Host "[run-dev] PostgreSQL LOCAL: OK (localhost:5432)" -ForegroundColor Green

# ---------------------------------------------------------------------------
# 3. Arrancar el back
# ---------------------------------------------------------------------------
if (-not $SkipMvn) {
    Write-Host "[run-dev] Iniciando Spring Boot con profile=dev..." -ForegroundColor Cyan
    # maven.test.skip: spring-boot:run resuelve classpath de test y dispara
    # test-compile; tests desalineados no deben bloquear el arranque local.
    & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dmaven.test.skip=true"
}
