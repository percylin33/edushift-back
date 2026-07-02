package com.edushift.modules.academic.year.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.year.dto.CreateAcademicYearRequest;
import com.edushift.modules.academic.year.dto.UpdateAcademicYearRequest;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AcademicYearMapperTest {

    private final AcademicYearMapper mapper = new AcademicYearMapper();

    private AcademicYear year;

    @BeforeEach
    void setUp() {
        year = new AcademicYear();
        year.setPublicUuid(UUID.randomUUID());
        year.setName("2026");
        year.setStatus(AcademicYearStatus.ACTIVE);
        year.setStartDate(LocalDate.of(2026, 3, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        year.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields")
        void mapsAllFields() {
            var resp = mapper.toResponse(year);
            assertThat(resp.publicUuid()).isEqualTo(year.getPublicUuid());
            assertThat(resp.name()).isEqualTo("2026");
            assertThat(resp.status()).isEqualTo(AcademicYearStatus.ACTIVE);
            assertThat(resp.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(resp.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        }
    }

    @Nested
    @DisplayName("toListItem")
    class ToListItem {

        @Test
        @DisplayName("maps without timestamps")
        void mapsFields() {
            var item = mapper.toListItem(year);
            assertThat(item.name()).isEqualTo("2026");
            assertThat(item.status()).isEqualTo(AcademicYearStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("fromCreate")
    class FromCreate {

        @Test
        @DisplayName("creates entity with PLANNING status")
        void createsEntity() {
            var req = new CreateAcademicYearRequest("2027",
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 12, 31));
            var entity = mapper.fromCreate(req);

            assertThat(entity.getName()).isEqualTo("2027");
            assertThat(entity.getStatus()).isEqualTo(AcademicYearStatus.PLANNING);
            assertThat(entity.getStartDate()).isEqualTo(LocalDate.of(2027, 3, 1));
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("updates provided fields")
        void partialUpdate() {
            var patch = new UpdateAcademicYearRequest("Updated",
                LocalDate.of(2026, 4, 1), null);
            mapper.applyUpdate(patch, year);

            assertThat(year.getName()).isEqualTo("Updated");
            assertThat(year.getStartDate()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(year.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        }

        @Test
        @DisplayName("trims name")
        void trimsName() {
            var patch = new UpdateAcademicYearRequest("  2026b  ", null, null);
            mapper.applyUpdate(patch, year);
            assertThat(year.getName()).isEqualTo("2026b");
        }

        @Test
        @DisplayName("all-null does nothing")
        void noop() {
            var patch = new UpdateAcademicYearRequest(null, null, null);
            mapper.applyUpdate(patch, year);
            assertThat(year.getName()).isEqualTo("2026");
        }
    }
}
