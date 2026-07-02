package com.edushift.modules.academic.year.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Year DTOs")
class DtoTest {

    @Test
    @DisplayName("AcademicYearResponse — constructor + accessors")
    void academicYearResponse() {
        var resp = new AcademicYearResponse(UUID.randomUUID(), "2026",
            AcademicYearStatus.ACTIVE,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 31),
            Instant.now(), Instant.now());
        assertThat(resp.name()).isEqualTo("2026");
        assertThat(resp.status()).isEqualTo(AcademicYearStatus.ACTIVE);
    }

    @Test
    @DisplayName("AcademicYearListItem — constructor + accessors")
    void academicYearListItem() {
        var item = new AcademicYearListItem(UUID.randomUUID(), "2026",
            AcademicYearStatus.ACTIVE,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 31));
        assertThat(item.name()).isEqualTo("2026");
    }

    @Test
    @DisplayName("CreateAcademicYearRequest — constructor + accessors")
    void createAcademicYearRequest() {
        var req = new CreateAcademicYearRequest("2026",
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 31));
        assertThat(req.name()).isEqualTo("2026");
        assertThat(req.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("UpdateAcademicYearRequest — isEmpty checks")
    void updateAcademicYearRequest() {
        var empty = new UpdateAcademicYearRequest(null, null, null);
        assertThat(empty.isEmpty()).isTrue();

        var nonEmpty = new UpdateAcademicYearRequest("2027", null, null);
        assertThat(nonEmpty.isEmpty()).isFalse();
        assertThat(nonEmpty.name()).isEqualTo("2027");
    }
}
