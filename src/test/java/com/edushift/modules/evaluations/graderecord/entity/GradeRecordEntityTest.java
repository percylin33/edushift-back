package com.edushift.modules.evaluations.graderecord.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.students.entity.Student;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GradeRecordEntityTest {

    @Test
    @DisplayName("default isActive is TRUE")
    void defaultActive() {
        var g = new GradeRecord();
        assertThat(g.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("setters + getters")
    void accessors() {
        var g = new GradeRecord();
        UUID puuid = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        g.setPublicUuid(puuid);
        g.setScore(new BigDecimal("15.50"));
        g.setLiteral("A");
        g.setComments("Excellent participation");
        g.setRecordedAt(now);
        g.setRecordedByUserId(userId);
        g.setIsActive(Boolean.FALSE);

        assertThat(g.getPublicUuid()).isEqualTo(puuid);
        assertThat(g.getScore()).isEqualByComparingTo("15.50");
        assertThat(g.getLiteral()).isEqualTo("A");
        assertThat(g.getComments()).isEqualTo("Excellent participation");
        assertThat(g.getRecordedAt()).isEqualTo(now);
        assertThat(g.getRecordedByUserId()).isEqualTo(userId);
        assertThat(g.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("associations are nullable until wired")
    void associationsDefaultNull() {
        var g = new GradeRecord();
        assertThat(g.getEvaluation()).isNull();
        assertThat(g.getStudent()).isNull();
    }

    @Test
    @DisplayName("associations can be set")
    void associationsCanBeSet() {
        var g = new GradeRecord();
        var ev = new Evaluation();
        var st = new Student();
        g.setEvaluation(ev);
        g.setStudent(st);
        assertThat(g.getEvaluation()).isSameAs(ev);
        assertThat(g.getStudent()).isSameAs(st);
    }

    @Test
    @DisplayName("equals + hashCode use the internal id")
    void equalsHashCode() throws Exception {
        var g1 = new GradeRecord();
        var g2 = new GradeRecord();
        var id = UUID.randomUUID();
        setField(g1, "id", id);
        setField(g2, "id", id);
        assertThat(g1).isEqualTo(g2);
        assertThat(g1.hashCode()).isEqualTo(g2.hashCode());

        var g3 = new GradeRecord();
        setField(g3, "id", UUID.randomUUID());
        assertThat(g1).isNotEqualTo(g3);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getSuperclass().getSuperclass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}