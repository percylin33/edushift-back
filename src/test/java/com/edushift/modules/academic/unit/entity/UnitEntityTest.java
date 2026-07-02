package com.edushift.modules.academic.unit.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnitEntityTest {

    @Test
    @DisplayName("default isActive is true")
    void defaults() {
        var u = new Unit();
        assertThat(u.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("markDeleted sets deletedAt")
    void markDeleted() {
        var u = new Unit();
        u.markDeleted();
        assertThat(u.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("restore clears deletedAt")
    void restore() {
        var u = new Unit();
        u.markDeleted();
        u.restore();
        assertThat(u.getDeletedAt()).isNull();
    }
}
