package com.edushift.modules.academic.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.unit.dto.CreateUnitRequest;
import com.edushift.modules.academic.unit.dto.UpdateUnitRequest;
import com.edushift.modules.academic.unit.entity.Unit;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UnitMapperTest {

    private final UnitMapper mapper = new UnitMapper();

    private Course course;
    private Unit unit;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setPublicUuid(UUID.randomUUID());
        course.setCode("MAT");
        course.setName("Matemática");

        unit = new Unit();
        unit.setPublicUuid(UUID.randomUUID());
        unit.setCourse(course);
        unit.setName("Unidad 1");
        unit.setDescription("Desc");
        unit.setDisplayOrder(1);
        unit.setStartDate(LocalDate.of(2026, 3, 1));
        unit.setEndDate(LocalDate.of(2026, 3, 31));
        unit.setIsActive(true);
        unit.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        unit.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields with sessionCount")
        void mapsAllFields() {
            var resp = mapper.toResponse(unit, 5L);

            assertThat(resp.publicUuid()).isEqualTo(unit.getPublicUuid());
            assertThat(resp.course().code()).isEqualTo("MAT");
            assertThat(resp.name()).isEqualTo("Unidad 1");
            assertThat(resp.sessionCount()).isEqualTo(5L);
            assertThat(resp.displayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("handles null course")
        void nullCourse() {
            unit.setCourse(null);
            var resp = mapper.toResponse(unit, 0L);
            assertThat(resp.course()).isNull();
        }
    }

    @Nested
    @DisplayName("toListItem")
    class ToListItem {

        @Test
        @DisplayName("maps with sessionCount")
        void mapsFields() {
            var item = mapper.toListItem(unit, 3L);
            assertThat(item.name()).isEqualTo("Unidad 1");
            assertThat(item.sessionCount()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("fromCreate")
    class FromCreate {

        @Test
        @DisplayName("creates entity from request")
        void createsEntity() {
            var req = new CreateUnitRequest("Unidad 2", null, null,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), true);
            var entity = mapper.fromCreate(req, course, 2);

            assertThat(entity.getCourse()).isEqualTo(course);
            assertThat(entity.getName()).isEqualTo("Unidad 2");
            assertThat(entity.getDisplayOrder()).isEqualTo(2);
            assertThat(entity.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("defaults isActive to true when null")
        void defaultsIsActive() {
            var req = new CreateUnitRequest("U3", null, null, null, null, null);
            var entity = mapper.fromCreate(req, course, 3);
            assertThat(entity.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("blank description becomes null")
        void blankDescription() {
            var req = new CreateUnitRequest("U4", "  ", null, null, null, null);
            var entity = mapper.fromCreate(req, course, 4);
            assertThat(entity.getDescription()).isNull();
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("updates provided fields")
        void partialUpdate() {
            var patch = new UpdateUnitRequest("Renamed", null,
                LocalDate.of(2026, 5, 1), null, false);
            mapper.applyUpdate(patch, unit);

            assertThat(unit.getName()).isEqualTo("Renamed");
            assertThat(unit.getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(unit.getIsActive()).isFalse();
            assertThat(unit.getEndDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        }

        @Test
        @DisplayName("blank description becomes null")
        void blankDescription() {
            var patch = new UpdateUnitRequest(null, "  ", null, null, null);
            mapper.applyUpdate(patch, unit);
            assertThat(unit.getDescription()).isNull();
        }
    }
}
