package com.edushift.modules.academic.competency.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CapacityEntityTest {

    @Test
    @DisplayName("default isActive is true")
    void defaults() {
        var c = new Capacity();
        assertThat(c.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("setters and getters work")
    void setters() {
        var competency = new Competency();
        competency.setPublicUuid(UUID.randomUUID());

        var c = new Capacity();
        c.setPublicUuid(UUID.randomUUID());
        c.setCompetency(competency);
        c.setCode("MAT_C1_CAP1");
        c.setName("Traduce");
        c.setDisplayOrder(1);
        c.setIsActive(false);

        assertThat(c.getCode()).isEqualTo("MAT_C1_CAP1");
        assertThat(c.getCompetency()).isEqualTo(competency);
        assertThat(c.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("markDeleted sets deletedAt")
    void markDeleted() {
        var c = new Capacity();
        c.markDeleted();
        assertThat(c.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("restore clears deletedAt")
    void restore() {
        var c = new Capacity();
        c.markDeleted();
        c.restore();
        assertThat(c.getDeletedAt()).isNull();
    }
}
