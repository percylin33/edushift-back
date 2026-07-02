package com.edushift.modules.academic.period.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.modules.academic.period.dto.CreateAcademicPeriodRequest;
import com.edushift.modules.academic.period.dto.UpdateAcademicPeriodRequest;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.year.entity.AcademicYear;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AcademicPeriodMapperTest {

    private final AcademicPeriodMapper mapper = new AcademicPeriodMapper();

    private AcademicYear year;
    private AcademicPeriod period;

    @BeforeEach
    void setUp() {
        year = new AcademicYear();
        year.setPublicUuid(UUID.randomUUID());
        year.setName("2026");

        period = new AcademicPeriod();
        period.setPublicUuid(UUID.randomUUID());
        period.setAcademicYear(year);
        period.setPeriodType(PeriodType.BIMESTRE);
        period.setOrdinal(1);
        period.setName("I Bimestre");
        period.setStartDate(LocalDate.of(2026, 3, 1));
        period.setEndDate(LocalDate.of(2026, 5, 31));
        period.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        period.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields")
        void mapsAllFields() {
            var resp = mapper.toResponse(period);
            assertThat(resp.publicUuid()).isEqualTo(period.getPublicUuid());
            assertThat(resp.academicYearPublicUuid()).isEqualTo(year.getPublicUuid());
            assertThat(resp.academicYearName()).isEqualTo("2026");
            assertThat(resp.periodType()).isEqualTo(PeriodType.BIMESTRE);
            assertThat(resp.ordinal()).isEqualTo(1);
            assertThat(resp.name()).isEqualTo("I Bimestre");
        }
    }

    @Nested
    @DisplayName("toListItem")
    class ToListItem {

        @Test
        @DisplayName("maps without timestamps")
        void mapsFields() {
            var item = mapper.toListItem(period);
            assertThat(item.publicUuid()).isEqualTo(period.getPublicUuid());
            assertThat(item.ordinal()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("generateName")
    class GenerateName {

        @Test
        @DisplayName("generates roman numeral + display label")
        void generatesName() {
            assertThat(mapper.generateName(PeriodType.BIMESTRE, 1)).isEqualTo("I Bimestre");
            assertThat(mapper.generateName(PeriodType.TRIMESTRE, 2)).isEqualTo("II Trimestre");
            assertThat(mapper.generateName(PeriodType.BIMESTRE, 4)).isEqualTo("IV Bimestre");
            assertThat(mapper.generateName(PeriodType.ANUAL, 1)).isEqualTo("I Anual");
        }

        @Test
        @DisplayName("throws for out of range ordinal")
        void throwsForInvalidOrdinal() {
            assertThatThrownBy(() -> mapper.generateName(PeriodType.BIMESTRE, 0))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> mapper.generateName(PeriodType.BIMESTRE, 100))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("handles 99 (max)")
        void handlesMax() {
            assertThat(mapper.generateName(PeriodType.BIMESTRE, 99))
                .isEqualTo("XCIX Bimestre");
        }
    }


}
