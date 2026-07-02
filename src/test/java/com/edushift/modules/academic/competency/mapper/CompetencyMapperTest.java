package com.edushift.modules.academic.competency.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.competency.dto.CompetencyListItem;
import com.edushift.modules.academic.competency.dto.CompetencyResponse;
import com.edushift.modules.academic.competency.dto.CreateCompetencyRequest;
import com.edushift.modules.academic.competency.dto.UpdateCompetencyRequest;
import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.course.entity.Course;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CompetencyMapperTest {

    private final CompetencyMapper mapper = new CompetencyMapper();

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
        competency.setDescription("Description");
        competency.setDisplayOrder(1);
        competency.setIsActive(true);
        competency.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        competency.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        capacity = new Capacity();
        capacity.setPublicUuid(UUID.randomUUID());
        capacity.setCode("MAT_C1_CAP1");
        capacity.setName("Traduce cantidades");
        capacity.setDisplayOrder(1);
        capacity.setIsActive(true);
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields with capacities")
        void mapsAllFields() {
            var response = mapper.toResponse(competency, List.of(capacity));

            assertThat(response.publicUuid()).isEqualTo(competency.getPublicUuid());
            assertThat(response.code()).isEqualTo("MAT_C1");
            assertThat(response.name()).isEqualTo("Resuelve problemas");
            assertThat(response.description()).isEqualTo("Description");
            assertThat(response.displayOrder()).isEqualTo(1);
            assertThat(response.isActive()).isTrue();
            assertThat(response.course().code()).isEqualTo("MAT");
            assertThat(response.capacities()).hasSize(1);
            assertThat(response.capacities().get(0).code()).isEqualTo("MAT_C1_CAP1");
        }

        @Test
        @DisplayName("handles null course")
        void nullCourse() {
            competency.setCourse(null);
            var response = mapper.toResponse(competency, List.of());
            assertThat(response.course()).isNull();
        }

        @Test
        @DisplayName("handles empty capacities")
        void emptyCapacities() {
            var response = mapper.toResponse(competency, List.of());
            assertThat(response.capacities()).isEmpty();
        }
    }

    @Nested
    @DisplayName("toListItem")
    class ToListItem {

        @Test
        @DisplayName("maps all fields")
        void mapsAllFields() {
            var item = mapper.toListItem(competency, 2L);

            assertThat(item.publicUuid()).isEqualTo(competency.getPublicUuid());
            assertThat(item.code()).isEqualTo("MAT_C1");
            assertThat(item.name()).isEqualTo("Resuelve problemas");
            assertThat(item.displayOrder()).isEqualTo(1);
            assertThat(item.isActive()).isTrue();
            assertThat(item.capacityCount()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("fromCreate")
    class FromCreate {

        @Test
        @DisplayName("creates entity from request")
        void createsEntity() {
            var request = new CreateCompetencyRequest("MAT_C2", "Nueva", "Desc", 2, true);
            var entity = mapper.fromCreate(request, course, 2);

            assertThat(entity.getCourse()).isEqualTo(course);
            assertThat(entity.getCode()).isEqualTo("MAT_C2");
            assertThat(entity.getName()).isEqualTo("Nueva");
            assertThat(entity.getDescription()).isEqualTo("Desc");
            assertThat(entity.getDisplayOrder()).isEqualTo(2);
            assertThat(entity.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("defaults isActive to true when null")
        void defaultsIsActive() {
            var request = new CreateCompetencyRequest("MAT_C2", "Nueva", null, 1, null);
            var entity = mapper.fromCreate(request, course, 1);
            assertThat(entity.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("handles blank description as null")
        void blankDescription() {
            var request = new CreateCompetencyRequest("MAT_C2", "Nueva", "  ", 1, null);
            var entity = mapper.fromCreate(request, course, 1);
            assertThat(entity.getDescription()).isNull();
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("updates only non-null fields")
        void partialUpdate() {
            var patch = new UpdateCompetencyRequest(null, "Updated name", null, false);
            mapper.applyUpdate(patch, competency);

            assertThat(competency.getCode()).isEqualTo("MAT_C1");
            assertThat(competency.getName()).isEqualTo("Updated name");
            assertThat(competency.getDescription()).isEqualTo("Description");
            assertThat(competency.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("blank description clears to null")
        void blankDescriptionClears() {
            var patch = new UpdateCompetencyRequest(null, null, "  ", null);
            mapper.applyUpdate(patch, competency);
            assertThat(competency.getDescription()).isNull();
        }

        @Test
        @DisplayName("all-null patch does nothing")
        void noopPatch() {
            var patch = new UpdateCompetencyRequest(null, null, null, null);
            mapper.applyUpdate(patch, competency);
            assertThat(competency.getName()).isEqualTo("Resuelve problemas");
        }
    }
}
