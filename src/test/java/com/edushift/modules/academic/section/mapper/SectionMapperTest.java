package com.edushift.modules.academic.section.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.section.dto.CreateSectionRequest;
import com.edushift.modules.academic.section.dto.UpdateSectionRequest;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SectionMapperTest {

    private final SectionMapper mapper = new SectionMapper();

    private AcademicYear year;
    private AcademicLevel level;
    private Grade grade;
    private Section section;

    @BeforeEach
    void setUp() {
        year = new AcademicYear();
        year.setPublicUuid(UUID.randomUUID());
        year.setName("2026");
        year.setStatus(AcademicYearStatus.ACTIVE);

        level = new AcademicLevel();
        level.setPublicUuid(UUID.randomUUID());
        level.setCode("PRIMARIA");
        level.setName("Primaria");

        grade = new Grade();
        grade.setPublicUuid(UUID.randomUUID());
        grade.setLevel(level);
        grade.setName("1ro Primaria");
        grade.setOrdinal(1);

        section = new Section();
        section.setPublicUuid(UUID.randomUUID());
        section.setAcademicYear(year);
        section.setGrade(grade);
        section.setName("A");
        section.setCapacity(30);
        section.setDisplayOrder(1);
        section.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        section.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields with full hierarchy")
        void mapsAllFields() {
            var resp = mapper.toResponse(section);
            assertThat(resp.publicUuid()).isEqualTo(section.getPublicUuid());
            assertThat(resp.academicYearPublicUuid()).isEqualTo(year.getPublicUuid());
            assertThat(resp.academicYearStatus()).isEqualTo("ACTIVE");
            assertThat(resp.gradePublicUuid()).isEqualTo(grade.getPublicUuid());
            assertThat(resp.levelCode()).isEqualTo("PRIMARIA");
            assertThat(resp.name()).isEqualTo("A");
            assertThat(resp.capacity()).isEqualTo(30);
        }

        @Test
        @DisplayName("handles null associations")
        void nullAssociations() {
            section.setAcademicYear(null);
            section.setGrade(null);
            var resp = mapper.toResponse(section);
            assertThat(resp.academicYearPublicUuid()).isNull();
            assertThat(resp.gradePublicUuid()).isNull();
            assertThat(resp.levelCode()).isNull();
        }
    }

    @Nested
    @DisplayName("toListItem")
    class ToListItem {

        @Test
        @DisplayName("maps without timestamps")
        void mapsFields() {
            var item = mapper.toListItem(section);
            assertThat(item.name()).isEqualTo("A");
            assertThat(item.levelCode()).isEqualTo("PRIMARIA");
        }
    }

    @Nested
    @DisplayName("fromCreate")
    class FromCreate {

        @Test
        @DisplayName("creates entity from request")
        void createsEntity() {
            var req = new CreateSectionRequest(year.getPublicUuid(),
                grade.getPublicUuid(), "B", 25, 2);
            var entity = mapper.fromCreate(req, year, grade);

            assertThat(entity.getAcademicYear()).isEqualTo(year);
            assertThat(entity.getGrade()).isEqualTo(grade);
            assertThat(entity.getName()).isEqualTo("B");
            assertThat(entity.getCapacity()).isEqualTo(25);
            assertThat(entity.getDisplayOrder()).isEqualTo(2);
        }

        @Test
        @DisplayName("defaults displayOrder to 1 when null")
        void defaultsDisplayOrder() {
            var req = new CreateSectionRequest(year.getPublicUuid(),
                grade.getPublicUuid(), "C", null, null);
            var entity = mapper.fromCreate(req, year, grade);
            assertThat(entity.getDisplayOrder()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("updates provided fields")
        void partialUpdate() {
            var patch = new UpdateSectionRequest("Renamed", null, 3);
            mapper.applyUpdate(patch, section);

            assertThat(section.getName()).isEqualTo("Renamed");
            assertThat(section.getDisplayOrder()).isEqualTo(3);
            assertThat(section.getCapacity()).isEqualTo(30);
        }

        @Test
        @DisplayName("trims name")
        void trimsName() {
            var patch = new UpdateSectionRequest("  Renamed  ", null, null);
            mapper.applyUpdate(patch, section);
            assertThat(section.getName()).isEqualTo("Renamed");
        }
    }
}
