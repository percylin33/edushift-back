# =============================================================================
# run-dev.ps1 — Arranca el backend de EduShift cargando primero el .env
# (PowerShell no lee .env automáticamente; este script lo parchea).
#
# Uso:
#   .\run-dev.ps1
#   .\run-dev.ps1 -SkipMvn
#
# Requiere que estés en la carpeta `edushift-back`.
# =============================================================================

param(
    [switch]$SkipMvn = $false
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# ---------------------------------------------------------------------------
# 1. Cargar .env en el process scope (Spring Boot los ve como env vars
#    gracias a la convención de mayúsculas con guión-bajo-a-punto).
# ---------------------------------------------------------------------------
$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) {
    Write-Host "[run-dev] .env no encontrado en $envFile" -ForegroundColor Red
    exit 1
}

Write-Host "[run-dev] Cargando .env..." -ForegroundColor Cyan
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    if ($line -notmatch "=") { return }
    $name, $value = $line.Split("=", 2)
    $name = $name.Trim()
    $value = $value.Trim()
    # Strip surrounding quotes.
    if ($value.StartsWith('"') -and $value.EndsWith('"')) { $value = $value.Substring(1, $value.Length - 2) }
    if ($value.StartsWith("'") -and $value.EndsWith("'")) { $value = $value.Substring(1, $value.Length - 2) }
    Set-Item -Path "Env:$name" -Value $value
}

# ---------------------------------------------------------------------------
# 2. Validar MiniMax (es lo que estamos probando).
# ---------------------------------------------------------------------------
if ($env:MINIMAX_ENABLED -eq "true") {
    Write-Host "[run-dev] MiniMax: ENABLED" -ForegroundColor Green
    Write-Host "[run-dev]   base-url: $env:MINIMAX_BASE_URL" -ForegroundColor Gray
    Write-Host "[run-dev]   model:    $env:MINIMAX_DEFAULT_MODEL" -ForegroundColor Gray
    Write-Host "[run-dev]   api-key:  $($env:MINIMAX_API_KEY.Substring(0, [Math]::Min(20, $env:MINIMAX_API_KEY.Length)))..." -ForegroundColor Gray
} else {
    Write-Host "[run-dev] MiniMax: DISABLED (MockLlmClient activo)" -ForegroundColor Yellow
}

if ($env:OPENROUTER_ENABLED -eq "true") {
    Write-Host "[run-dev] OpenRouter: ENABLED" -ForegroundColor Green
} else {
    Write-Host "[run-dev] OpenRouter: DISABLED" -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# 3. Arrancar el back (a menos que -SkipMvn).
# ---------------------------------------------------------------------------
if (-not $SkipMvn) {
    Write-Host "[run-dev] Iniciando Spring Boot con profile=dev..." -ForegroundColor Cyan
    & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
}
