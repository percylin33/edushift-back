@echo off
REM =============================================================================
REM check-and-fix-tests.bat
REM Sprint 14 / BE-14.11 — Diagnóstico para tests fallidos de Maven (Windows)
REM
REM Uso:
REM   scripts\dev\check-and-fix-tests.bat          :: corre mvn test y diagnostica
REM   scripts\dev\check-and-fix-tests.bat --fix     :: aplica heurísticas
REM   scripts\dev\check-and-fix-tests.bat --dry-run :: solo muestra qué haría
REM =============================================================================
setlocal enabledelayedexpansion

echo ========================================
echo   check-and-fix-tests.bat - EduShift
echo ========================================

set "PROJECT_DIR=%~dp0..\.."
cd /d "%PROJECT_DIR%"

echo [1/3] Ejecutando mvn test...
call .\mvnw test > target\test-output.log 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [✖] mvn test fallo - revisando reportes...
) else (
    echo [✓] mvn test PASO - 0 failures
    exit /b 0
)

echo [2/3] Extrayendo tests fallidos...
findstr /R "Failures: [1-9] FAILURE" target\test-output.log > nul 2>&1
if %ERRORLEVEL% EQU 0 (
    findstr /R "Failures: [1-9] FAILURE" target\test-output.log
) else (
    echo No se encontraron fallos en el log.
)

echo.
echo [3/3] Resumen de clases fallidas:
if exist "target\surefire-reports" (
    for %%f in ("target\surefire-reports\*.txt") do (
        findstr /C:"FAILURE" "%%f" > nul 2>&1
        if !ERRORLEVEL! EQU 0 (
            echo   - %%~nf
        )
    )
) else (
    echo No se encontraron reportes Surefire.
)

echo.
echo ========================================
echo  Diagnostico completado.
echo  Revisa target\test-output.log para detalle.
echo ========================================
