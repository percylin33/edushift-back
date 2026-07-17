package com.edushift.modules.evaluations.rubric.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.evaluations.rubric.config.RubricSeedDefaults;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import com.edushift.modules.evaluations.rubric.mapper.RubricMapper;
import com.edushift.modules.evaluations.rubric.repository.RubricRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RubricSeedServiceImpl} (Sprint 5B / BE-5B.2 /
 * ADR-5B.10).
 *
 * <p>The static catalog in {@link RubricSeedDefaults} is real; the
 * repository mock drives the diff so we can verify both the "first run"
 * and the "idempotent re-run" paths.</p>
 */
@ExtendWith(MockitoExtension.class)
class RubricSeedServiceImplTest {

    @Mock private RubricRepository repository;
    @Spy private RubricMapper mapper = new RubricMapper();

    @InjectMocks private RubricSeedServiceImpl service;

    @Nested
    @DisplayName("materializeSystemRubrics")
    class Materialize {

        @Test
        @DisplayName("empty tenant → inserts all seed rows")
        void firstRun() {
            // The production code calls findFiltered twice:
            //   - 1st to get the "existing" rows (empty for first run)
            //   - 2nd after inserts to get the refreshed list
            // Mockito strict mode flags a chained .thenReturn() with
            // more entries than actual calls as UnnecessaryStubbing —
            // we only need 2 returns.
            when(repository.findFiltered(eq(true), eq(true), any()))
                    .thenReturn(List.of())
                    .thenReturn(seedRowsForAll());

            var result = service.materializeSystemRubrics();

            assertThat(result).hasSize(RubricSeedDefaults.size());
            verify(repository, times(RubricSeedDefaults.size())).save(any(Rubric.class));
        }

        @Test
        @DisplayName("tenant already has some seed rows → skips existing, inserts only missing")
        void idempotentPartial() {
            String firstName = RubricSeedDefaults.all().get(0).name();
            Rubric existing = existingRubric(firstName);

            when(repository.findFiltered(eq(true), eq(true), any()))
                    .thenReturn(List.of(existing))
                    .thenReturn(seedRowsForAll());

            var result = service.materializeSystemRubrics();

            assertThat(result).hasSize(RubricSeedDefaults.size());
            int expectedInserts = RubricSeedDefaults.size() - 1;
            verify(repository, times(expectedInserts)).save(any(Rubric.class));
        }

        @Test
        @DisplayName("tenant already has all seed rows → no inserts")
        void idempotentFull() {
            when(repository.findFiltered(eq(true), eq(true), any()))
                    .thenReturn(seedRowsForAll())
                    .thenReturn(seedRowsForAll());

            var result = service.materializeSystemRubrics();

            assertThat(result).hasSize(RubricSeedDefaults.size());
            verify(repository, never()).save(any(Rubric.class));
        }
    }

    @Nested
    @DisplayName("RubricSeedDefaults catalog")
    class Defaults {

        @Test
        @DisplayName("catalog contains all 10 expected MINEDU rubrics")
        void catalog() {
            assertThat(RubricSeedDefaults.size()).isEqualTo(10);
            var names = RubricSeedDefaults.all().stream().map(RubricSeedDefaults.DefaultRubric::name).toList();
            assertThat(names).contains(
                    "Ensayo argumentativo",
                    "Exposicion oral",
                    "Proyecto de investigacion",
                    "Examen de matematicas",
                    "Practica de laboratorio",
                    "Resolucion de problemas",
                    "Trabajo en equipo",
                    "Lectura comprensiva",
                    "Produccion escrita",
                    "Mapa conceptual");
        }

        @Test
        @DisplayName("containsName is case-insensitive and trims")
        void containsName() {
            assertThat(RubricSeedDefaults.containsName("Ensayo argumentativo")).isTrue();
            assertThat(RubricSeedDefaults.containsName("  ENSAYO ARGUMENTATIVO  ")).isTrue();
            assertThat(RubricSeedDefaults.containsName("Nonexistent")).isFalse();
            assertThat(RubricSeedDefaults.containsName(null)).isFalse();
        }

        @Test
        @DisplayName("countByName — duplicate names return > 1 (defence in depth)")
        void countByName() {
            assertThat(RubricSeedDefaults.countByName("Ensayo argumentativo")).isEqualTo(1);
            assertThat(RubricSeedDefaults.countByName("  ensayo argumentativo  ")).isEqualTo(1);
            assertThat(RubricSeedDefaults.countByName("Nonexistent")).isZero();
            assertThat(RubricSeedDefaults.countByName(null)).isZero();
        }

        @Test
        @DisplayName("LEVEL_DISPLAY_NAMES maps the four canonical codes")
        void displayNames() {
            assertThat(RubricSeedDefaults.LEVEL_DISPLAY_NAMES).hasSize(4);
            assertThat(RubricSeedDefaults.LEVEL_DISPLAY_NAMES.get("EN_INICIO")).isEqualTo("En inicio");
            assertThat(RubricSeedDefaults.LEVEL_DISPLAY_NAMES.get("SOBRESALIENTE")).isEqualTo("Sobresaliente");
        }

        @Test
        @DisplayName("each seed rubric has criteria summing to 100")
        void weightsSum() {
            for (var r : RubricSeedDefaults.all()) {
                var sum = r.criteria().stream()
                        .map(c -> c.weight())
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                assertThat(sum)
                        .as("rubric '%s' weights", r.name())
                        .isEqualByComparingTo(java.math.BigDecimal.valueOf(100));
            }
        }

        @Test
        @DisplayName("each seed rubric has 4 canonical levels")
        void levelsShape() {
            for (var r : RubricSeedDefaults.all()) {
                assertThat(r.levels()).hasSize(4);
                assertThat(r.levels().stream().map(l -> l.code()).toList())
                        .containsExactly("EN_INICIO", "EN_PROCESO", "ESPERADO", "SOBRESALIENTE");
            }
        }
    }

    private static List<Rubric> seedRowsForAll() {
        List<Rubric> out = new java.util.ArrayList<>();
        for (var def : RubricSeedDefaults.all()) {
            Rubric r = new Rubric();
            try {
                setField(r, "publicUuid", UUID.randomUUID());
                setField(r, "id", UUID.randomUUID());
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
            r.setName(def.name());
            r.setIsSystem(true);
            r.setIsActive(true);
            out.add(r);
        }
        return out;
    }

    private static Rubric existingRubric(String name) {
        Rubric r = new Rubric();
        try {
            setField(r, "publicUuid", UUID.randomUUID());
            setField(r, "id", UUID.randomUUID());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        r.setName(name);
        r.setIsSystem(true);
        r.setIsActive(true);
        return r;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        // `id` lives on BaseEntity (3 levels up from Rubric);
        // `publicUuid` lives directly on Rubric. Walk up the hierarchy
        // and try each level — the first match wins.
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " not found on " + target.getClass());
    }
}