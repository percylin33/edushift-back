package com.edushift.modules.academic.course.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CourseEntityTest {

    @Test
    @DisplayName("default isActive is true")
    void defaults() {
        var c = new Course();
        assertThat(c.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("markDeleted sets deletedAt")
    void markDeleted() {
        var c = new Course();
        c.markDeleted();
        assertThat(c.getDeletedAt()).isNotNull();
        assertThat(c.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("restore clears deletedAt")
    void restore() {
        var c = new Course();
        c.markDeleted();
        c.restore();
        assertThat(c.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("CourseLevel markDeleted/restore")
    void courseLevelLifecycle() {
        var cl = new CourseLevel();
        cl.markDeleted();
        assertThat(cl.getDeletedAt()).isNotNull();
        assertThat(cl.isDeleted()).isTrue();
        cl.restore();
        assertThat(cl.getDeletedAt()).isNull();
    }
}
