package com.edushift.modules.academic.year.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class YearEntityTest {

    @Test
    @DisplayName("default status is PLANNING")
    void defaults() {
        var y = new AcademicYear();
        assertThat(y.getStatus()).isEqualTo(AcademicYearStatus.PLANNING);
    }

    @Test
    @DisplayName("AcademicYearStatus values")
    void statusValues() {
        assertThat(AcademicYearStatus.valueOf("PLANNING")).isEqualTo(AcademicYearStatus.PLANNING);
        assertThat(AcademicYearStatus.valueOf("ACTIVE")).isEqualTo(AcademicYearStatus.ACTIVE);
        assertThat(AcademicYearStatus.valueOf("CLOSED")).isEqualTo(AcademicYearStatus.CLOSED);
    }

    @Test
    @DisplayName("markDeleted sets deletedAt")
    void markDeleted() {
        var y = new AcademicYear();
        y.markDeleted();
        assertThat(y.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("restore clears deletedAt")
    void restore() {
        var y = new AcademicYear();
        y.markDeleted();
        y.restore();
        assertThat(y.getDeletedAt()).isNull();
    }
}
