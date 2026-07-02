package com.edushift.modules.academic.levelgrade.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Level/Grade DTOs")
class DtoTest {

    @Test
    @DisplayName("AcademicLevelResponse — constructor + accessors")
    void academicLevelResponse() {
        var gradeResp = new GradeResponse(UUID.randomUUID(), UUID.randomUUID(), "1ro", 1,
            Instant.now(), Instant.now());
        var resp = new AcademicLevelResponse(UUID.randomUUID(), "PRIMARIA", "Primaria", 2,
            List.of(gradeResp), Instant.now(), Instant.now());
        assertThat(resp.code()).isEqualTo("PRIMARIA");
        assertThat(resp.grades()).hasSize(1);
    }

    @Test
    @DisplayName("CreateAcademicLevelRequest — constructor + accessors")
    void createAcademicLevelRequest() {
        var req = new CreateAcademicLevelRequest("IGCSE", "IGCSE", 4);
        assertThat(req.code()).isEqualTo("IGCSE");
        assertThat(req.ordinal()).isEqualTo(4);
    }

    @Test
    @DisplayName("UpdateAcademicLevelRequest — constructor + accessors")
    void updateAcademicLevelRequest() {
        var req = new UpdateAcademicLevelRequest("NEW", "New", 5);
        assertThat(req.code()).isEqualTo("NEW");
        assertThat(req.ordinal()).isEqualTo(5);
    }

    @Test
    @DisplayName("GradeResponse — constructor + accessors")
    void gradeResponse() {
        var resp = new GradeResponse(UUID.randomUUID(), UUID.randomUUID(), "1ro", 1,
            Instant.now(), Instant.now());
        assertThat(resp.name()).isEqualTo("1ro");
        assertThat(resp.ordinal()).isEqualTo(1);
    }

    @Test
    @DisplayName("CreateGradeRequest — constructor + accessors")
    void createGradeRequest() {
        var req = new CreateGradeRequest("1ro", 1);
        assertThat(req.name()).isEqualTo("1ro");
        assertThat(req.ordinal()).isEqualTo(1);
    }

    @Test
    @DisplayName("UpdateGradeRequest — constructor + accessors")
    void updateGradeRequest() {
        var req = new UpdateGradeRequest("2do", 2);
        assertThat(req.name()).isEqualTo("2do");
    }

    @Test
    @DisplayName("GradeReorderRequest — constructor + accessors")
    void gradeReorderRequest() {
        var item = new GradeReorderRequest.Item(UUID.randomUUID(), 1);
        var req = new GradeReorderRequest(List.of(item));
        assertThat(req.items()).hasSize(1);
        assertThat(req.items().get(0).ordinal()).isEqualTo(1);
    }
}
