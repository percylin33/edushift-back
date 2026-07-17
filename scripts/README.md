# Scripts de desarrollo — EduShift Backend

## Diagnóstico de tests

### `scripts/dev/check-and-fix-tests.sh` (Unix) / `.bat` (Windows)

Corre `mvn test`, extrae las clases con tests fallidos y aplica heurísticas
conocidas para diagnosticar patrones de fallo repetitivos.

```bash
# Modo diagnóstico (solo muestra qué falla)
./scripts/dev/check-and-fix-tests.sh

# Vista previa de las heurísticas que aplicarían
./scripts/dev/check-and-fix-tests.sh --dry-run

# Aplica heurísticas conocidas (e.g. añadir mocks faltantes)
./scripts/dev/check-and-fix-tests.sh --fix
```

**Heurísticas implementadas:**
1. **`@WebMvcTest` + body vacío** — detecta `content("{}")` en POST con `@Valid`
2. **Falta `currentUserId()` mock** — detecta `CurrentUserProvider` sin `when(currentUser.currentUserId())`
3. **Falta `ScheduledExecutorService` mock** — detecta `@InjectMocks` sin heartbeat mock

**Output:**
- `target/test-output.log` — log completo de `mvn test`
- Reportes Surefire en `target/surefire-reports/`

## Smoke tests (PowerShell)

Los scripts `smoke-be*.ps1` ejecutan smoke tests E2E contra la API.
Requisitos: backend corriendo en `localhost:8080` con perfil `dev`.

```powershell
.\scripts\smoke-be67.ps1
```

## Utilidades

| Script | Propósito |
|--------|-----------|
| `strip-bom.ps1` | Elimina BOM UTF-8 de archivos |
| `cleanup-tecnosur.sql` | Limpia datos de prueba del seed |
| `fix-passwords-edu2026.sql` | Resetea passwords del seed |
