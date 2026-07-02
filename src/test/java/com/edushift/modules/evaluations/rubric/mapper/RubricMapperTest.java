package com.edushift.modules.evaluations.rubric.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.evaluations.rubric.dto.CriterionInput;
import com.edushift.modules.evaluations.rubric.dto.DescriptorInput;
import com.edushift.modules.evaluations.rubric.dto.LevelInput;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RubricMapperTest {

    private final RubricMapper mapper = new RubricMapper();

    private Rubric rubric;
    private Rubric parent;

    @BeforeEach
    void setUp() throws Exception {
        parent = new Rubric();
        setField(parent, "publicUuid", UUID.randomUUID());

        rubric = new Rubric();
        setField(rubric, "publicUuid", UUID.randomUUID());
        rubric.setName("My Rubric");
        rubric.setDescription("My description");
        rubric.setIsSystem(Boolean.FALSE);
        rubric.setIsActive(Boolean.TRUE);
        rubric.setParentRubric(parent);
        rubric.setCriteria(List.of(
                criterionMap("clarity", "Clarity", 25, "A", "Achieved"),
                criterionMap("cohesion", "Cohesion", 30, "A", "OK"),
                criterionMap("structure", "Structure", 45, "A", "Good")));
        rubric.setLevels(List.of(
                levelMap("EN_INICIO", "En inicio", 1),
                levelMap("EN_PROCESO", "En proceso", 2),
                levelMap("ESPERADO", "Esperado", 3),
                levelMap("SOBRESALIENTE", "Sobresaliente", 4)));
        setField(rubric, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        setField(rubric, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields")
        void mapsAllFields() {
            var resp = mapper.toResponse(rubric);

            assertThat(resp.publicUuid()).isEqualTo(rubric.getPublicUuid());
            assertThat(resp.name()).isEqualTo("My Rubric");
            assertThat(resp.description()).isEqualTo("My description");
            assertThat(resp.isSystem()).isFalse();
            assertThat(resp.isActive()).isTrue();
            assertThat(resp.parentRubricPublicUuid()).isEqualTo(parent.getPublicUuid());

            assertThat(resp.criteria()).hasSize(3);
            assertThat(resp.criteria().get(0).key()).isEqualTo("clarity");
            assertThat(resp.criteria().get(0).weight()).isEqualByComparingTo(BigDecimal.valueOf(25));
            assertThat(resp.criteria().get(0).descriptors()).hasSize(1);

            assertThat(resp.levels()).hasSize(4);
            assertThat(resp.levels().get(0).code()).isEqualTo("EN_INICIO");
            assertThat(resp.levels().get(3).order()).isEqualTo(4);
        }

        @Test
        @DisplayName("null parent — parentRubricPublicUuid is null")
        void nullParent() {
            rubric.setParentRubric(null);
            var resp = mapper.toResponse(rubric);
            assertThat(resp.parentRubricPublicUuid()).isNull();
        }

        @Test
        @DisplayName("null criteria / levels map to empty lists")
        void emptyCollections() {
            rubric.setCriteria(null);
            rubric.setLevels(null);
            var resp = mapper.toResponse(rubric);
            assertThat(resp.criteria()).isEmpty();
            assertThat(resp.levels()).isEmpty();
        }
    }

    @Nested
    @DisplayName("toListItem")
    class ToListItem {

        @Test
        @DisplayName("builds a summary with name and weight")
        void summary() {
            var item = mapper.toListItem(rubric);

            assertThat(item.publicUuid()).isEqualTo(rubric.getPublicUuid());
            assertThat(item.name()).isEqualTo("My Rubric");
            assertThat(item.description()).isEqualTo("My description");
            assertThat(item.isSystem()).isFalse();
            assertThat(item.criterionCount()).isEqualTo(3);
            assertThat(item.criterionSummary()).hasSize(3);
            assertThat(item.criterionSummary().get(0)).contains("Clarity").contains("25");
            assertThat(item.parentRubricPublicUuid()).isEqualTo(parent.getPublicUuid());
        }

        @Test
        @DisplayName("summary without weight still includes the name")
        void summaryWithoutWeight() {
            rubric.setCriteria(List.of(criterionMap("only", "Only name", null, null, null)));
            var item = mapper.toListItem(rubric);
            assertThat(item.criterionSummary()).containsExactly("Only name");
        }

        @Test
        @DisplayName("null criteria — empty list, no crash")
        void nullCriteria() {
            rubric.setCriteria(null);
            var item = mapper.toListItem(rubric);
            assertThat(item.criterionCount()).isZero();
            assertThat(item.criterionSummary()).isEmpty();
        }
    }

    @Nested
    @DisplayName("fromCreate")
    class FromCreate {

        @Test
        @DisplayName("creates entity with isSystem=false, isActive=true")
        void creates() {
            var c = new CriterionInput("c1", "C1", null,
                    new BigDecimal("100.00"),
                    List.of(new DescriptorInput("A", "Achieved")));
            var l = new LevelInput("A", "A", 1);

            Rubric r = mapper.fromCreate("New", "Desc", List.of(c), List.of(l));

            assertThat(r.getName()).isEqualTo("New");
            assertThat(r.getDescription()).isEqualTo("Desc");
            assertThat(r.getIsSystem()).isFalse();
            assertThat(r.getIsActive()).isTrue();
            assertThat(r.getCriteria()).hasSize(1);
            assertThat(r.getCriteria().get(0).get("key")).isEqualTo("c1");
            assertThat(r.getLevels()).hasSize(1);
            assertThat(r.getLevels().get(0).get("order")).isEqualTo(1);
        }

        @Test
        @DisplayName("blank description becomes null")
        void blankDescription() {
            var c = new CriterionInput("c1", "C1", null,
                    new BigDecimal("100.00"),
                    List.of(new DescriptorInput("A", "OK")));
            var l = new LevelInput("A", "A", 1);
            Rubric r = mapper.fromCreate("New", "   ", List.of(c), List.of(l));
            assertThat(r.getDescription()).isNull();
        }

        @Test
        @DisplayName("null description stays null")
        void nullDescription() {
            var c = new CriterionInput("c1", "C1", null,
                    new BigDecimal("100.00"),
                    List.of(new DescriptorInput("A", "OK")));
            var l = new LevelInput("A", "A", 1);
            Rubric r = mapper.fromCreate("New", null, List.of(c), List.of(l));
            assertThat(r.getDescription()).isNull();
        }

        @Test
        @DisplayName("level without explicit order defaults to its index")
        void defaultLevelOrder() {
            var c = new CriterionInput("c1", "C1", null,
                    new BigDecimal("100.00"),
                    List.of(new DescriptorInput("A", "OK")));
            var l0 = new LevelInput("A", "A", null);
            var l1 = new LevelInput("B", "B", null);
            Rubric r = mapper.fromCreate("New", null, List.of(c), List.of(l0, l1));
            assertThat(r.getLevels().get(0).get("order")).isEqualTo(0);
            assertThat(r.getLevels().get(1).get("order")).isEqualTo(1);
        }

        @Test
        @DisplayName("criterion with no descriptors is representable")
        void criterionNoDescriptors() {
            var c = new CriterionInput("c1", "C1", null,
                    new BigDecimal("100.00"), null);
            var l = new LevelInput("A", "A", 1);
            Rubric r = mapper.fromCreate("New", null, List.of(c), List.of(l));
            assertThat(r.getCriteria().get(0).get("descriptors")).isEqualTo(List.of());
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("partial-merge — non-null fields replace")
        void partialMerge() {
            var newCriteria = List.of(new CriterionInput("k", "n", null,
                    new BigDecimal("100.00"),
                    List.of(new DescriptorInput("A", "OK"))));
            var newLevels = List.of(new LevelInput("A", "A", 1),
                    new LevelInput("B", "B", 2));

            mapper.applyUpdate("Renamed", "New desc", newCriteria, newLevels, rubric);

            assertThat(rubric.getName()).isEqualTo("Renamed");
            assertThat(rubric.getDescription()).isEqualTo("New desc");
            assertThat(rubric.getCriteria()).hasSize(1);
            assertThat(rubric.getLevels()).hasSize(2);
        }

        @Test
        @DisplayName("nulls are skipped")
        void nullsAreSkipped() {
            String originalName = rubric.getName();
            mapper.applyUpdate(null, null, null, null, rubric);
            assertThat(rubric.getName()).isEqualTo(originalName);
        }

        @Test
        @DisplayName("blank description is normalized to null")
        void blankDescription() {
            mapper.applyUpdate(null, "   ", null, null, rubric);
            assertThat(rubric.getDescription()).isNull();
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private static Map<String, Object> criterionMap(String key, String name, Integer weight,
            String level, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("name", name);
        if (weight != null) {
            m.put("weight", BigDecimal.valueOf(weight));
        }
        else {
            m.put("weight", null);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("level", level);
        d.put("text", text);
        m.put("descriptors", List.of(d));
        return m;
    }

    private static Map<String, Object> levelMap(String code, String name, int order) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("order", order);
        return m;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            }
            catch (NoSuchFieldException ignore) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}