package com.edushift.modules.evaluations.rubric.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.modules.evaluations.rubric.dto.CriterionInput;
import com.edushift.modules.evaluations.rubric.dto.DescriptorInput;
import com.edushift.modules.evaluations.rubric.dto.LevelInput;
import com.edushift.modules.evaluations.rubric.error.RubricErrorCodes;
import com.edushift.shared.exception.BadRequestException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RubricValidationService} (Sprint 5B / BE-5B.2).
 *
 * <p>Every test exercises the public surface ({@code assertShapeValid},
 * {@code assertCriteriaShapeValid}, {@code assertLevelsShapeValid}).
 * Each rule maps to one of the {@code RUB_*} error codes.</p>
 */
class RubricValidationServiceTest {

    private final RubricValidationService service = new RubricValidationService();

    private static CriterionInput criterion(String key, String name, String weight) {
        return new CriterionInput(key, name, null, new BigDecimal(weight),
                List.of(new DescriptorInput("A", "Achieved")));
    }

    private static CriterionInput criterionWithDescriptor(String key, String weight,
            String level) {
        return new CriterionInput(key, "name", null, new BigDecimal(weight),
                List.of(new DescriptorInput(level, "text")));
    }

    private static List<LevelInput> defaultLevels() {
        return List.of(
                new LevelInput("EN_INICIO", "En inicio", 1),
                new LevelInput("EN_PROCESO", "En proceso", 2),
                new LevelInput("ESPERADO", "Esperado", 3),
                new LevelInput("SOBRESALIENTE", "Sobresaliente", 4));
    }

    private static List<CriterionInput> validCriteria() {
        return List.of(
                criterion("clarity", "Clarity", "25.00"),
                criterion("cohesion", "Cohesion", "30.00"),
                criterion("structure", "Structure", "45.00"));
    }

    @Nested
    @DisplayName("assertShapeValid — criteria count")
    class CriteriaCount {

        @Test
        @DisplayName("0 criteria → RUB_CRITERIA_COUNT")
        void empty() {
            assertThatThrownBy(() -> service.assertShapeValid(List.of(), defaultLevels()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_CRITERIA_COUNT");
        }

        @Test
        @DisplayName("11 criteria → RUB_CRITERIA_COUNT")
        void tooMany() {
            var many = new java.util.ArrayList<CriterionInput>();
            for (int i = 0; i < 11; i++) {
                many.add(criterion("k" + i, "n" + i, "9.09"));
            }
            assertThatThrownBy(() -> service.assertShapeValid(many, defaultLevels()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_CRITERIA_COUNT");
        }

        @Test
        @DisplayName("1 criterion is allowed")
        void single() {
            assertThatNoException().isThrownBy(() -> service.assertShapeValid(
                    List.of(criterion("only", "Only", "100.00")), defaultLevels()));
        }

        @Test
        @DisplayName("10 criteria are allowed")
        void ten() {
            var ten = new java.util.ArrayList<CriterionInput>();
            for (int i = 0; i < 10; i++) {
                ten.add(criterion("k" + i, "n" + i, "10.00"));
            }
            assertThatNoException().isThrownBy(() -> service.assertShapeValid(ten, defaultLevels()));
        }
    }

    @Nested
    @DisplayName("assertShapeValid — levels count")
    class LevelsCount {

        @Test
        @DisplayName("1 level → RUB_LEVELS_COUNT")
        void tooFew() {
            assertThatThrownBy(() -> service.assertShapeValid(
                    validCriteria(), List.of(new LevelInput("A", "A", 1))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_LEVELS_COUNT");
        }

        @Test
        @DisplayName("5 levels → RUB_LEVELS_COUNT")
        void tooMany() {
            assertThatThrownBy(() -> service.assertShapeValid(
                    validCriteria(),
                    List.of(
                            new LevelInput("A", "A", 1),
                            new LevelInput("B", "B", 2),
                            new LevelInput("C", "C", 3),
                            new LevelInput("D", "D", 4),
                            new LevelInput("E", "E", 5))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_LEVELS_COUNT");
        }
    }

    @Nested
    @DisplayName("assertShapeValid — level codes unique")
    class LevelsUnique {

        @Test
        @DisplayName("duplicate level code → RUB_LEVEL_CODE_DUPLICATE")
        void duplicateCode() {
            var levels = List.of(
                    new LevelInput("A", "First A", 1),
                    new LevelInput("A", "Second A", 2),
                    new LevelInput("B", "B", 3),
                    new LevelInput("C", "C", 4));
            assertThatThrownBy(() -> service.assertShapeValid(validCriteria(), levels))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_LEVEL_CODE_DUPLICATE");
        }

        @Test
        @DisplayName("case-insensitive — 'a' / 'A' collide")
        void caseInsensitive() {
            var levels = List.of(
                    new LevelInput("a", "a", 1),
                    new LevelInput("A", "A", 2),
                    new LevelInput("B", "B", 3),
                    new LevelInput("C", "C", 4));
            assertThatThrownBy(() -> service.assertShapeValid(validCriteria(), levels))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_LEVEL_CODE_DUPLICATE");
        }
    }

    @Nested
    @DisplayName("assertShapeValid — criterion keys unique")
    class CriteriaUniqueKeys {

        @Test
        @DisplayName("duplicate key → RUB_CRITERION_KEY_DUPLICATE")
        void duplicateKey() {
            var criteria = List.of(
                    criterion("dup", "A", "50.00"),
                    criterion("dup", "B", "50.00"));
            assertThatThrownBy(() -> service.assertShapeValid(criteria, defaultLevels()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_CRITERION_KEY_DUPLICATE");
        }
    }

    @Nested
    @DisplayName("assertShapeValid — weights sum")
    class WeightsSum {

        @Test
        @DisplayName("sum != 100 → RUB_CRITERIA_WEIGHT_SUM")
        void wrongSum() {
            var criteria = List.of(
                    criterion("a", "a", "30.00"),
                    criterion("b", "b", "30.00"));
            assertThatThrownBy(() -> service.assertShapeValid(criteria, defaultLevels()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_CRITERIA_WEIGHT_SUM");
        }

        @Test
        @DisplayName("weight > 100 → RUB_CRITERIA_WEIGHT_SUM")
        void weightOverHundred() {
            var criteria = List.of(criterion("a", "a", "150.00"));
            assertThatThrownBy(() -> service.assertShapeValid(criteria, defaultLevels()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_CRITERIA_WEIGHT_SUM");
        }

        @Test
        @DisplayName("negative weight → RUB_CRITERIA_WEIGHT_SUM")
        void negativeWeight() {
            var criteria = List.of(criterion("a", "a", "-1.00"));
            assertThatThrownBy(() -> service.assertShapeValid(criteria, defaultLevels()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_CRITERIA_WEIGHT_SUM");
        }

        @Test
        @DisplayName("sum within 0.01 epsilon of 100.00 is accepted")
        void epsilonTolerance() {
            var criteria = List.of(
                    criterion("a", "a", "50.00"),
                    criterion("b", "b", "50.005"));
            assertThatNoException().isThrownBy(() -> service.assertShapeValid(criteria, defaultLevels()));
        }
    }

    @Nested
    @DisplayName("assertShapeValid — descriptor cross-reference")
    class DescriptorRefs {

        @Test
        @DisplayName("descriptor level not in levels[] → RUB_LEVEL_UNKNOWN")
        void unknownLevel() {
            var criteria = List.of(criterionWithDescriptor("a", "100.00", "GHOST"));
            assertThatThrownBy(() -> service.assertShapeValid(criteria, defaultLevels()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_LEVEL_UNKNOWN");
        }

        @Test
        @DisplayName("two descriptors on same criterion for same level → RUB_DESCRIPTOR_DUPLICATE")
        void duplicateDescriptor() {
            var c = new CriterionInput("a", "a", null, new BigDecimal("100.00"),
                    List.of(
                            new DescriptorInput("A", "first"),
                            new DescriptorInput("A", "second")));
            assertThatThrownBy(() -> service.assertShapeValid(List.of(c), defaultLevels()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_DESCRIPTOR_DUPLICATE");
        }
    }

    @Nested
    @DisplayName("assertCriteriaShapeValid / assertLevelsShapeValid")
    class PartialHelpers {

        @Test
        @DisplayName("assertCriteriaShapeValid — null is a no-op")
        void criteriaNullIsNoOp() {
            assertThatNoException().isThrownBy(() -> service.assertCriteriaShapeValid(null));
        }

        @Test
        @DisplayName("assertLevelsShapeValid — null is a no-op")
        void levelsNullIsNoOp() {
            assertThatNoException().isThrownBy(() -> service.assertLevelsShapeValid(null));
        }

        @Test
        @DisplayName("assertCriteriaShapeValid — duplicate keys still fail")
        void criteriaDuplicateKey() {
            var criteria = List.of(
                    criterion("a", "a", "50.00"),
                    criterion("a", "b", "50.00"));
            assertThatThrownBy(() -> service.assertCriteriaShapeValid(criteria))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_CRITERION_KEY_DUPLICATE");
        }

        @Test
        @DisplayName("assertLevelsShapeValid — duplicate codes still fail")
        void levelsDuplicateCode() {
            var levels = List.of(
                    new LevelInput("A", "A", 1),
                    new LevelInput("A", "A", 2));
            assertThatThrownBy(() -> service.assertLevelsShapeValid(levels))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("RUB_LEVEL_CODE_DUPLICATE");
        }

        @Test
        @DisplayName("criteria-only helper skips descriptor cross-reference (needs levels)")
        void criteriaOnlySkipsCrossRef() {
            var c = criterionWithDescriptor("a", "100.00", "GHOST");
            assertThatNoException().isThrownBy(() -> service.assertCriteriaShapeValid(List.of(c)));
        }
    }

    @Test
    @DisplayName("valid shape — no exception")
    void validShape() {
        assertThatNoException().isThrownBy(() ->
                service.assertShapeValid(validCriteria(), defaultLevels()));
    }

    @Test
    @DisplayName("error code constants are referenced")
    void codeConstants() {
        assertThat(RubricErrorCodes.RUB_CRITERIA_WEIGHT_SUM).isEqualTo("RUB_CRITERIA_WEIGHT_SUM");
    }
}