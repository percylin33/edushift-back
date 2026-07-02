package com.edushift.modules.academic.competency.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.course.entity.Course;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompetencyEntityTest {

    @Test
    @DisplayName("default isActive is true")
    void defaults() {
        var c = new Competency();
        assertThat(c.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("setters and getters work")
    void setters() {
        var course = new Course();
        course.setPublicUuid(UUID.randomUUID());

        var c = new Competency();
        c.setPublicUuid(UUID.randomUUID());
        c.setCourse(course);
        c.setCode("MAT_C1");
        c.setName("Resuelve problemas");
        c.setDescription("Desc");
        c.setDisplayOrder(1);
        c.setIsActive(false);

        assertThat(c.getCode()).isEqualTo("MAT_C1");
        assertThat(c.getCourse()).isEqualTo(course);
        assertThat(c.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("markDeleted sets deletedAt")
    void markDeleted() {
        var c = new Competency();
        c.markDeleted();
        assertThat(c.getDeletedAt()).isNotNull();
        assertThat(c.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("restore clears deletedAt")
    void restore() {
        var c = new Competency();
        c.markDeleted();
        c.restore();
        assertThat(c.getDeletedAt()).isNull();
        assertThat(c.isDeleted()).isFalse();
    }
}
