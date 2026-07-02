package com.edushift.modules.academic.competency.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Competency DTOs")
class DtoTest {

    @Test
    @DisplayName("CompetencyResponse — constructor + accessors")
    void competencyResponse() {
        var id = UUID.randomUUID();
        var courseRef = new CompetencyResponse.CourseRef(UUID.randomUUID(), "MAT", "Mat");
        var capRef = new CompetencyResponse.CapacityRef(UUID.randomUUID(), "C1", "Cap1", 1, true);
        var created = Instant.parse("2026-01-01T00:00:00Z");
        var updated = Instant.parse("2026-01-02T00:00:00Z");

        var resp = new CompetencyResponse(id, courseRef, "MAT_C1", "Resuelve", null, 1, true,
            List.of(capRef), created, updated);

        assertThat(resp.publicUuid()).isEqualTo(id);
        assertThat(resp.code()).isEqualTo("MAT_C1");
        assertThat(resp.name()).isEqualTo("Resuelve");
        assertThat(resp.displayOrder()).isEqualTo(1);
        assertThat(resp.isActive()).isTrue();
        assertThat(resp.capacities()).hasSize(1);
        assertThat(resp.course().code()).isEqualTo("MAT");
    }

    @Test
    @DisplayName("CompetencyListItem — constructor + accessors")
    void competencyListItem() {
        var item = new CompetencyListItem(UUID.randomUUID(), "MAT_C1", "Resuelve", 1, true, 2L);
        assertThat(item.code()).isEqualTo("MAT_C1");
        assertThat(item.capacityCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("CreateCompetencyRequest — constructor + accessors")
    void createCompetencyRequest() {
        var req = new CreateCompetencyRequest("MAT_C1", "Resuelve", "Desc", 1, true);
        assertThat(req.code()).isEqualTo("MAT_C1");
        assertThat(req.name()).isEqualTo("Resuelve");
        assertThat(req.displayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("UpdateCompetencyRequest — constructor + accessors")
    void updateCompetencyRequest() {
        var req = new UpdateCompetencyRequest("MAT_C2", "Updated", "Desc", false);
        assertThat(req.code()).isEqualTo("MAT_C2");
        assertThat(req.isActive()).isFalse();
    }

    @Test
    @DisplayName("CompetencyReorderRequest — constructor + accessors")
    void competencyReorderRequest() {
        var item1 = new CompetencyReorderRequest.Item(UUID.randomUUID(), 1);
        var item2 = new CompetencyReorderRequest.Item(UUID.randomUUID(), 2);
        var req = new CompetencyReorderRequest(List.of(item1, item2));
        assertThat(req.items()).hasSize(2);
        assertThat(req.items().get(0).displayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("CapacityResponse — constructor + accessors")
    void capacityResponse() {
        var courseRef = new CapacityResponse.CourseRef(UUID.randomUUID(), "MAT", "Mat");
        var compRef = new CapacityResponse.CompetencyRef(
            UUID.randomUUID(), "MAT_C1", "Resuelve", courseRef);
        var resp = new CapacityResponse(UUID.randomUUID(), compRef, "MAT_C1_CAP1", "Traduce",
            null, 1, true, Instant.now(), Instant.now());
        assertThat(resp.code()).isEqualTo("MAT_C1_CAP1");
        assertThat(resp.competency().code()).isEqualTo("MAT_C1");
    }

    @Test
    @DisplayName("CreateCapacityRequest — constructor + accessors")
    void createCapacityRequest() {
        var req = new CreateCapacityRequest("MAT_C1_CAP1", "Traduce", "Desc", 1, true);
        assertThat(req.code()).isEqualTo("MAT_C1_CAP1");
        assertThat(req.displayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("UpdateCapacityRequest — constructor + accessors")
    void updateCapacityRequest() {
        var req = new UpdateCapacityRequest(null, "Updated", null, null);
        assertThat(req.name()).isEqualTo("Updated");
    }

    @Test
    @DisplayName("CapacityReorderRequest — constructor + accessors")
    void capacityReorderRequest() {
        var item = new CapacityReorderRequest.Item(UUID.randomUUID(), 2);
        var req = new CapacityReorderRequest(List.of(item));
        assertThat(req.items()).hasSize(1);
        assertThat(req.items().get(0).displayOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("SeedCompetenciesResponse — constructor + accessors")
    void seedCompetenciesResponse() {
        var resp = new SeedCompetenciesResponse(true, false, "MAT", 2, 3, List.of());
        assertThat(resp.courseCode()).isEqualTo("MAT");
        assertThat(resp.seeded()).isTrue();
        assertThat(resp.competenciesCreated()).isEqualTo(2);
        assertThat(resp.capacitiesCreated()).isEqualTo(3);
    }
}
