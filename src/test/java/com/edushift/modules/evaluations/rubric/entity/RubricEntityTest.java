package com.edushift.modules.evaluations.rubric.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RubricEntityTest {

    @Test
    @DisplayName("default isSystem is FALSE")
    void defaultIsSystem() {
        var r = new Rubric();
        assertThat(r.getIsSystem()).isFalse();
    }

    @Test
    @DisplayName("default isActive is TRUE")
    void defaultIsActive() {
        var r = new Rubric();
        assertThat(r.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("setters + getters")
    void accessors() {
        var r = new Rubric();
        UUID puuid = UUID.randomUUID();
        r.setPublicUuid(puuid);
        r.setName("My Rubric");
        r.setDescription("Description");
        r.setCriteria(List.of(Map.of("key", "k1", "weight", 50)));
        r.setLevels(List.of(Map.of("code", "A", "order", 1)));
        r.setIsSystem(Boolean.TRUE);
        r.setIsActive(Boolean.FALSE);
        r.setDeletedAt(Instant.now());

        assertThat(r.getPublicUuid()).isEqualTo(puuid);
        assertThat(r.getName()).isEqualTo("My Rubric");
        assertThat(r.getDescription()).isEqualTo("Description");
        assertThat(r.getCriteria()).hasSize(1);
        assertThat(r.getLevels()).hasSize(1);
        assertThat(r.getIsSystem()).isTrue();
        assertThat(r.getIsActive()).isFalse();
        assertThat(r.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("parentRubric is nullable and settable")
    void parentRubric() {
        var r = new Rubric();
        assertThat(r.getParentRubric()).isNull();
        var parent = new Rubric();
        r.setParentRubric(parent);
        assertThat(r.getParentRubric()).isSameAs(parent);
    }

    @Test
    @DisplayName("equals + hashCode use the internal id")
    void equalsHashCode() throws Exception {
        var r1 = new Rubric();
        var r2 = new Rubric();
        var id = UUID.randomUUID();
        setField(r1, "id", id);
        setField(r2, "id", id);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());

        var r3 = new Rubric();
        setField(r3, "id", UUID.randomUUID());
        assertThat(r1).isNotEqualTo(r3);
    }

    @Test
    @DisplayName("JSONB lists can be set with LinkedHashMap to preserve order")
    void jsonbLinkedMap() {
        var r = new Rubric();
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("key", "k1");
        ordered.put("name", "Criterion 1");
        ordered.put("weight", 50);
        r.setCriteria(List.of(ordered));
        assertThat(r.getCriteria()).first().satisfies(m -> {
            assertThat(m.keySet()).containsExactly("key", "name", "weight");
        });
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getSuperclass().getSuperclass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}