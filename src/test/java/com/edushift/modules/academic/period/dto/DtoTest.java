package com.edushift.modules.academic.period.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.period.entity.PeriodType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Period DTOs")
class DtoTest {

    @Test
    @DisplayName("AcademicPeriodResponse — constructor + accessors")
    void academicPeriodResponse() {
        var resp = new AcademicPeriodResponse(UUID.randomUUID(), UUID.randomUUID(), "2026",
            PeriodType.BIMESTRE, 1, "I Bimestre",
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31),
            Instant.now(), Instant.now());
        assertThat(resp.periodType()).isEqualTo(PeriodType.BIMESTRE);
        assertThat(resp.ordinal()).isEqualTo(1);
    }

    @Test
    @DisplayName("AcademicPeriodListItem — constructor + accessors")
    void academicPeriodListItem() {
        var item = new AcademicPeriodListItem(UUID.randomUUID(), UUID.randomUUID(),
            PeriodType.BIMESTRE, 1, "I Bimestre",
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31));
        assertThat(item.name()).isEqualTo("I Bimestre");
    }

    @Test
    @DisplayName("CreateAcademicPeriodRequest — constructor + accessors")
    void createAcademicPeriodRequest() {
        var req = new CreateAcademicPeriodRequest(UUID.randomUUID(), PeriodType.BIMESTRE, 1,
            "I Bimestre", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31));
        assertThat(req.ordinal()).isEqualTo(1);
        assertThat(req.periodType()).isEqualTo(PeriodType.BIMESTRE);
    }

    @Test
    @DisplayName("UpdateAcademicPeriodRequest — isEmpty checks")
    void updateAcademicPeriodRequest() {
        var empty = new UpdateAcademicPeriodRequest(null, null, null);
        assertThat(empty.isEmpty()).isTrue();

        var nonEmpty = new UpdateAcademicPeriodRequest("II Bimestre", null, null);
        assertThat(nonEmpty.isEmpty()).isFalse();
        assertThat(nonEmpty.name()).isEqualTo("II Bimestre");
    }
}
