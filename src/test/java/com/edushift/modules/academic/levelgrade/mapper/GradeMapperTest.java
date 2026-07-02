package com.edushift.modules.academic.levelgrade.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.levelgrade.dto.CreateGradeRequest;
import com.edushift.modules.academic.levelgrade.dto.UpdateGradeRequest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GradeMapperTest {

    private final GradeMapper mapper = new GradeMapper();

    private AcademicLevel level;
    private Grade grade;

    @BeforeEach
    void setUp() {
        level = new AcademicLevel();
        level.setPublicUuid(UUID.randomUUID());
        level.setCode("PRIMARIA");

        grade = new Grade();
        grade.setPublicUuid(UUID.randomUUID());
        grade.setLevel(level);
        grade.setName("1ro Primaria");
        grade.setOrdinal(1);
        grade.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        grade.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields")
        void mapsAllFields() {
            var resp = mapper.toResponse(grade);

            assertThat(resp.publicUuid()).isEqualTo(grade.getPublicUuid());
            assertThat(resp.levelPublicUuid()).isEqualTo(level.getPublicUuid());
            assertThat(resp.name()).isEqualTo("1ro Primaria");
            assertThat(resp.ordinal()).isEqualTo(1);
        }

        @Test
        @DisplayName("handles null level")
        void nullLevel() {
            grade.setLevel(null);
            var resp = mapper.toResponse(grade);
            assertThat(resp.levelPublicUuid()).isNull();
        }
    }

    @Nested
    @DisplayName("fromCreate")
    class FromCreate {

        @Test
        @DisplayName("creates entity from request")
        void createsEntity() {
            var req = new CreateGradeRequest("2do Primaria", 2);
            var entity = mapper.fromCreate(req, level);

            assertThat(entity.getLevel()).isEqualTo(level);
            assertThat(entity.getName()).isEqualTo("2do Primaria");
            assertThat(entity.getOrdinal()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("updates name and strips whitespace")
        void updateName() {
            var patch = new UpdateGradeRequest("  2do Primaria  ", null);
            mapper.applyUpdate(patch, grade);
            assertThat(grade.getName()).isEqualTo("2do Primaria");
        }

        @Test
        @DisplayName("updates ordinal")
        void updateOrdinal() {
            var patch = new UpdateGradeRequest(null, 3);
            mapper.applyUpdate(patch, grade);
            assertThat(grade.getOrdinal()).isEqualTo(3);
        }
    }
}
