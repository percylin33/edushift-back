package com.edushift.modules.academic.competency.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.competency.dto.CreateCapacityRequest;
import com.edushift.modules.academic.competency.dto.UpdateCapacityRequest;
import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.course.entity.Course;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CapacityMapperTest {

    private final CapacityMapper mapper = new CapacityMapper();

    private Course course;
    private Competency competency;
    private Capacity capacity;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setPublicUuid(UUID.randomUUID());
        course.setCode("MAT");
        course.setName("Matemática");

        competency = new Competency();
        competency.setPublicUuid(UUID.randomUUID());
        competency.setCourse(course);
        competency.setCode("MAT_C1");
        competency.setName("Resuelve problemas");
        competency.setDisplayOrder(1);

        capacity = new Capacity();
        capacity.setPublicUuid(UUID.randomUUID());
        capacity.setCompetency(competency);
        capacity.setCode("MAT_C1_CAP1");
        capacity.setName("Traduce cantidades");
        capacity.setDescription("Desc");
        capacity.setDisplayOrder(1);
        capacity.setIsActive(true);
        capacity.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        capacity.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields with competency + course refs")
        void mapsAllFields() {
            var response = mapper.toResponse(capacity);

            assertThat(response.publicUuid()).isEqualTo(capacity.getPublicUuid());
            assertThat(response.code()).isEqualTo("MAT_C1_CAP1");
            assertThat(response.name()).isEqualTo("Traduce cantidades");
            assertThat(response.description()).isEqualTo("Desc");
            assertThat(response.displayOrder()).isEqualTo(1);
            assertThat(response.isActive()).isTrue();
            assertThat(response.competency().code()).isEqualTo("MAT_C1");
            assertThat(response.competency().course().code()).isEqualTo("MAT");
        }

        @Test
        @DisplayName("handles null competency")
        void nullCompetency() {
            capacity.setCompetency(null);
            var response = mapper.toResponse(capacity);
            assertThat(response.competency()).isNull();
        }
    }

    @Nested
    @DisplayName("fromCreate")
    class FromCreate {

        @Test
        @DisplayName("creates entity from request")
        void createsEntity() {
            var request = new CreateCapacityRequest("MAT_C1_CAP2", "Nueva", "Desc", 2, false);
            var entity = mapper.fromCreate(request, competency, 2);

            assertThat(entity.getCompetency()).isEqualTo(competency);
            assertThat(entity.getCode()).isEqualTo("MAT_C1_CAP2");
            assertThat(entity.getName()).isEqualTo("Nueva");
            assertThat(entity.getDescription()).isEqualTo("Desc");
            assertThat(entity.getDisplayOrder()).isEqualTo(2);
            assertThat(entity.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("defaults isActive to true")
        void defaultsIsActive() {
            var request = new CreateCapacityRequest("C1", "Nueva", null, 1, null);
            var entity = mapper.fromCreate(request, competency, 1);
            assertThat(entity.getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("updates only non-null fields")
        void partialUpdate() {
            var patch = new UpdateCapacityRequest(null, "Updated", null, null);
            mapper.applyUpdate(patch, capacity);

            assertThat(capacity.getCode()).isEqualTo("MAT_C1_CAP1");
            assertThat(capacity.getName()).isEqualTo("Updated");
        }

        @Test
        @DisplayName("blank description clears to null")
        void blankDescription() {
            var patch = new UpdateCapacityRequest(null, null, "  ", null);
            mapper.applyUpdate(patch, capacity);
            assertThat(capacity.getDescription()).isNull();
        }
    }
}
