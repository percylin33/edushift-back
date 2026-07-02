package com.edushift.modules.academic.levelgrade.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.levelgrade.dto.CreateAcademicLevelRequest;
import com.edushift.modules.academic.levelgrade.dto.UpdateAcademicLevelRequest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AcademicLevelMapperTest {

    private final GradeMapper gradeMapper = new GradeMapper();
    private final AcademicLevelMapper mapper = new AcademicLevelMapper(gradeMapper);

    private AcademicLevel level;
    private Grade grade;

    @BeforeEach
    void setUp() {
        level = new AcademicLevel();
        level.setPublicUuid(UUID.randomUUID());
        level.setCode("PRIMARIA");
        level.setName("Primaria");
        level.setOrdinal(2);
        level.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        level.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        grade = new Grade();
        grade.setPublicUuid(UUID.randomUUID());
        grade.setLevel(level);
        grade.setName("1ro Primaria");
        grade.setOrdinal(1);
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields with grades")
        void mapsAllFields() {
            var resp = mapper.toResponse(level, List.of(grade));

            assertThat(resp.publicUuid()).isEqualTo(level.getPublicUuid());
            assertThat(resp.code()).isEqualTo("PRIMARIA");
            assertThat(resp.ordinal()).isEqualTo(2);
            assertThat(resp.grades()).hasSize(1);
            assertThat(resp.grades().get(0).name()).isEqualTo("1ro Primaria");
        }

        @Test
        @DisplayName("handles null grades")
        void nullGrades() {
            var resp = mapper.toResponse(level, null);
            assertThat(resp.grades()).isEmpty();
        }

        @Test
        @DisplayName("handles empty grades")
        void emptyGrades() {
            var resp = mapper.toResponse(level, List.of());
            assertThat(resp.grades()).isEmpty();
        }
    }

    @Nested
    @DisplayName("fromCreate")
    class FromCreate {

        @Test
        @DisplayName("creates entity from request")
        void createsEntity() {
            var req = new CreateAcademicLevelRequest("IGCSE", "IGCSE", 4);
            var entity = mapper.fromCreate(req);

            assertThat(entity.getCode()).isEqualTo("IGCSE");
            assertThat(entity.getName()).isEqualTo("IGCSE");
            assertThat(entity.getOrdinal()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("updates only non-null fields")
        void partialUpdate() {
            var patch = new UpdateAcademicLevelRequest(null, "Renamed", null);
            mapper.applyUpdate(patch, level);
            assertThat(level.getName()).isEqualTo("Renamed");
            assertThat(level.getCode()).isEqualTo("PRIMARIA");
        }

        @Test
        @DisplayName("all-null patch does nothing")
        void noop() {
            var patch = new UpdateAcademicLevelRequest(null, null, null);
            mapper.applyUpdate(patch, level);
            assertThat(level.getCode()).isEqualTo("PRIMARIA");
        }
    }
}
