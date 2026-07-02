package com.edushift.modules.evaluations.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvaluationEntityTest {

    @Test
    @DisplayName("default status is DRAFT")
    void defaultStatus() {
        var e = new Evaluation();
        assertThat(e.getStatus()).isEqualTo(EvaluationStatus.DRAFT);
    }

    @Test
    @DisplayName("default isActive is TRUE")
    void defaultActive() {
        var e = new Evaluation();
        assertThat(e.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("setters + getters")
    void accessors() {
        var e = new Evaluation();
        UUID puuid = UUID.randomUUID();
        e.setPublicUuid(puuid);
        e.setKind(EvaluationKind.EXAM);
        e.setName("Midterm");
        e.setDescription("Pencil and paper");
        e.setWeight(new BigDecimal("25.00"));
        e.setScheduledDate(LocalDate.of(2026, 5, 1));
        e.setDueDate(LocalDate.of(2026, 5, 8));
        e.setScale(EvaluationScale.SCORE_0_20);
        e.setStatus(EvaluationStatus.PUBLISHED);
        e.setIsActive(Boolean.FALSE);

        assertThat(e.getPublicUuid()).isEqualTo(puuid);
        assertThat(e.getKind()).isEqualTo(EvaluationKind.EXAM);
        assertThat(e.getName()).isEqualTo("Midterm");
        assertThat(e.getDescription()).isEqualTo("Pencil and paper");
        assertThat(e.getWeight()).isEqualByComparingTo("25.00");
        assertThat(e.getScheduledDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(e.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 8));
        assertThat(e.getScale()).isEqualTo(EvaluationScale.SCORE_0_20);
        assertThat(e.getStatus()).isEqualTo(EvaluationStatus.PUBLISHED);
        assertThat(e.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("associations are nullable until wired")
    void associationsDefaultNull() {
        var e = new Evaluation();
        assertThat(e.getTeacherAssignment()).isNull();
        assertThat(e.getUnit()).isNull();
        assertThat(e.getLearningSession()).isNull();
    }

    @Test
    @DisplayName("associations can be set")
    void associationsCanBeSet() {
        var e = new Evaluation();
        var ta = new TeacherAssignment();
        var unit = new Unit();
        e.setTeacherAssignment(ta);
        e.setUnit(unit);
        assertThat(e.getTeacherAssignment()).isSameAs(ta);
        assertThat(e.getUnit()).isSameAs(unit);
    }

    @Test
    @DisplayName("BaseEntity equals + hashCode use the internal id")
    void equalsHashCode() throws Exception {
        var e1 = newEvaluation();
        var e2 = newEvaluation();
        var sameId = setInternalId(e1, UUID.randomUUID());
        setInternalId(e2, sameId);
        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());

        var different = newEvaluation();
        setInternalId(different, UUID.randomUUID());
        assertThat(e1).isNotEqualTo(different);
    }

    private static Evaluation newEvaluation() throws Exception {
        var e = new Evaluation();
        setField(e, "id", null);
        return e;
    }

    private static UUID setInternalId(Evaluation e, UUID id) throws Exception {
        setField(e, "id", id);
        return id;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getSuperclass().getSuperclass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}