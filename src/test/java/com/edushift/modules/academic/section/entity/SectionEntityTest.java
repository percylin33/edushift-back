package com.edushift.modules.academic.section.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SectionEntityTest {

    @Test
    @DisplayName("default displayOrder is null (set in @PrePersist)")
    void defaults() {
        var s = new Section();
        s.setName("A");
        assertThat(s.getName()).isEqualTo("A");
    }

    @Test
    @DisplayName("markDeleted sets deletedAt")
    void markDeleted() {
        var s = new Section();
        s.markDeleted();
        assertThat(s.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("restore clears deletedAt")
    void restore() {
        var s = new Section();
        s.markDeleted();
        s.restore();
        assertThat(s.getDeletedAt()).isNull();
    }
}
