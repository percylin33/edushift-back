package com.edushift.modules.academic.period.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.year.entity.AcademicYear;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeriodEntityTest {

    @Test
    @DisplayName("PeriodType displayLabel values")
    void periodTypeLabels() {
        assertThat(PeriodType.BIMESTRE.displayLabel()).isEqualTo("Bimestre");
        assertThat(PeriodType.TRIMESTRE.displayLabel()).isEqualTo("Trimestre");
        assertThat(PeriodType.ANUAL.displayLabel()).isEqualTo("Anual");
    }

    @Test
    @DisplayName("AcademicPeriod defaults and setters")
    void academicPeriod() {
        var year = new AcademicYear();
        year.setName("2026");

        var p = new AcademicPeriod();
        p.setAcademicYear(year);
        p.setPeriodType(PeriodType.BIMESTRE);
        p.setOrdinal(1);
        p.setName("I Bimestre");
        p.setStartDate(LocalDate.of(2026, 3, 1));
        p.setEndDate(LocalDate.of(2026, 5, 31));

        assertThat(p.getPeriodType()).isEqualTo(PeriodType.BIMESTRE);
        assertThat(p.getName()).isEqualTo("I Bimestre");
        assertThat(p.getAcademicYear().getName()).isEqualTo("2026");
    }

    @Test
    @DisplayName("markDeleted sets deletedAt")
    void markDeleted() {
        var p = new AcademicPeriod();
        p.markDeleted();
        assertThat(p.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("restore clears deletedAt")
    void restore() {
        var p = new AcademicPeriod();
        p.markDeleted();
        p.restore();
        assertThat(p.getDeletedAt()).isNull();
    }
}
