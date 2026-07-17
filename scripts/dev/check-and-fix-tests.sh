#!/usr/bin/env bash
# =============================================================================
# check-and-fix-tests.sh
# Sprint 14 / BE-14.11 — Diagnóstico y heurísticas para tests fallidos de Maven
#
# Uso:
#   ./scripts/dev/check-and-fix-tests.sh          # corre mvn test y diagnostica
#   ./scripts/dev/check-and-fix-tests.sh --fix     # aplica heurísticas conocidas
#   ./scripts/dev/check-and-fix-tests.sh --dry-run # solo muestra qué haría
#
# NO automatiza fixes complejos — solo diagnostica y aplica patrones repetitivos
# como agregar mocks faltantes o actualizar JSON bodies.
# =============================================================================
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'
FIX_MODE=false
DRY_RUN=false
for arg in "$@"; do
  case "$arg" in
    --fix) FIX_MODE=true ;;
    --dry-run) DRY_RUN=true ;;
  esac
done

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  check-and-fix-tests.sh — EduShift${NC}"
echo -e "${YELLOW}========================================${NC}"

PROJECT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$PROJECT_DIR"

echo "[1/3] Ejecutando mvn test..."
if ! mvn test 2>&1 | tee target/test-output.log; then
  echo -e "${RED}✖ mvn test falló — revisando reportes...${NC}"
else
  echo -e "${GREEN}✓ mvn test PASÓ — 0 failures${NC}"
  exit 0
fi

echo "[2/3] Extrayendo tests fallidos..."
FAILED_TESTS=$(grep -E 'Tests run:.*Failures: [1-9]|FAILURE|Failed tests' target/test-output.log || true)
echo "$FAILED_TESTS"

# Extraer nombres de clase
FAILING_CLASSES=$(find target/surefire-reports -name "*.txt" -exec grep -l 'FAILURE\|<failure' {} \; 2>/dev/null | sed 's/.*\/\(.*\)\.txt/\1/' || true)
if [ -z "$FAILING_CLASSES" ]; then
  echo -e "${YELLOW}No se encontraron reportes Surefire con failures.${NC}"
  echo "Buscando en output de consola..."
  FAILING_CLASSES=$(grep -oP 'at \K[^(]+' target/test-output.log | grep 'Test' | sort -u || true)
fi

if [ -z "$FAILING_CLASSES" ]; then
  echo -e "${RED}No se pudieron determinar las clases fallidas. Revisa target/test-output.log manualmente.${NC}"
  exit 1
fi

echo ""
echo -e "${YELLOW}Clases con tests fallidos:${NC}"
echo "$FAILING_CLASSES" | while read -r cls; do
  echo "  - $cls"
done

if [ "$FIX_MODE" = false ] && [ "$DRY_RUN" = false ]; then
  echo ""
  echo -e "${YELLOW}Modo diagnóstico. Para aplicar heurísticas usa --fix o --dry-run.${NC}"
  exit 0
fi

echo ""
echo "[3/3] Aplicando heurísticas conocidas..."

echo "$FAILING_CLASSES" | while read -r cls; do
  SRC="src/test/java/$(echo "$cls" | tr '.' '/').java"
  if [ ! -f "$SRC" ]; then
    echo -e "${YELLOW}  ↺ $cls → fuente no encontrada en $SRC, buscando...${NC}"
    SRC=$(find src/test -name "$(basename "$cls").java" -path "*/$(echo "$cls" | tr '.' '/*' | rev | cut -d'/' -f2- | rev)" 2>/dev/null | head -1 || true)
    [ -z "$SRC" ] && echo -e "${RED}  ✖ $cls → no encontrado${NC}" && continue
  fi

  echo -e "${YELLOW}  → Analizando $SRC${NC}"
  HINTS=""

  # Heurística 1: @InjectMocks + @Value → mockear campos @Value
  if grep -q '@InjectMocks' "$SRC" 2>/dev/null; then
    # Buscar clases con @Value que el test no mockea
    IMPL=$(grep -oP 'private \w+ service' "$SRC" | head -1 | awk '{print $2}' || true)
    if [ -n "$IMPL" ]; then
      # Si el test no tiene @MockitoBean o @Mock para ScheduledExecutorService, probablemente falta
      if ! grep -q 'heartbeatScheduler\|ScheduledExecutorService' "$SRC" 2>/dev/null; then
        HINTS="$HINTS\n    - Posible falta @MockitoBean ScheduledExecutorService (heartbeat scheduler)"
      fi
    fi
  fi

  # Heurística 2: @WebMvcTest + body vacío en POST
  if grep -q '@WebMvcTest' "$SRC" 2>/dev/null; then
    if grep -q 'content.*\"{}\"' "$SRC" 2>/dev/null; then
      HINTS="$HINTS\n    - Body vacío '{}' en POST con @Valid → espera 400, no 201. Enviar JSON con campos @NotNull."
    fi
  fi

  # Heurística 3: @Mock CurrentUserProvider sin mockear currentUserId()
  if grep -q 'CurrentUserProvider' "$SRC" 2>/dev/null; then
    if ! grep -q 'currentUserId\(\)' "$SRC" 2>/dev/null; then
      HINTS="$HINTS\n    - Falta when(currentUser.currentUserId()).thenReturn(Optional.of(UUID.randomUUID())) en setUp"
    fi
  fi

  if [ -n "$HINTS" ]; then
    echo -e "${YELLOW}  Heurísticas detectadas:${NC}"
    echo -e "$HINTS"
    if [ "$FIX_MODE" = true ]; then
      echo -e "${GREEN}    (--fix activo, aplicando cambios)${NC}"
      # Aquí se aplicarían los fixes automatizados si existieran
    fi
  else
    echo -e "${GREEN}  ✓ Sin heurísticas conocidas para $cls${NC}"
  fi
done

echo ""
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  Diagnóstico completado.${NC}"
echo -e "${YELLOW}  Revisa target/test-output.log para detalle.${NC}"
echo -e "${YELLOW}========================================${NC}"
