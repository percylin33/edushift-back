package com.edushift.modules.academic.course.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.course.CourseVisualDefaults;
import com.edushift.modules.academic.course.dto.CreateCourseRequest;
import com.edushift.modules.academic.course.dto.UpdateCourseRequest;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.entity.CourseLevel;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CourseMapperTest {

    private final CourseMapper mapper = new CourseMapper();

    private Course course;
    private AcademicLevel level;
    private CourseLevel link;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setPublicUuid(UUID.randomUUID());
        course.setCode("MAT");
        course.setName("Matemática");
        course.setDescription("Desc");
        course.setCredits(4);
        course.setHoursPerWeek(5);
        course.setIsActive(true);
        course.setColor("#3B82F6");
        course.setIcon("target");
        course.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        course.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        level = new AcademicLevel();
        level.setPublicUuid(UUID.randomUUID());
        level.setCode("PRIMARIA");
        level.setName("Primaria");
        level.setOrdinal(2);

        link = new CourseLevel();
        link.setCourse(course);
        link.setLevel(level);
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields with levels")
        void mapsAllFields() {
            var resp = mapper.toResponse(course, List.of(link));

            assertThat(resp.publicUuid()).isEqualTo(course.getPublicUuid());
            assertThat(resp.code()).isEqualTo("MAT");
            assertThat(resp.name()).isEqualTo("Matemática");
            assertThat(resp.credits()).isEqualTo(4);
            assertThat(resp.color()).isEqualTo("#3B82F6");
            assertThat(resp.icon()).isEqualTo("target");
            assertThat(resp.levels()).hasSize(1);
            assertThat(resp.levels().get(0).code()).isEqualTo("PRIMARIA");
        }

        @Test
        @DisplayName("handles empty levels")
        void emptyLevels() {
            var resp = mapper.toResponse(course, List.of());
            assertThat(resp.levels()).isEmpty();
        }
    }

    @Nested
    @DisplayName("toListItem")
    class ToListItem {

        @Test
        @DisplayName("maps without timestamps")
        void mapsFields() {
            var item = mapper.toListItem(course, List.of(link));

            assertThat(item.publicUuid()).isEqualTo(course.getPublicUuid());
            assertThat(item.code()).isEqualTo("MAT");
            assertThat(item.color()).isEqualTo("#3B82F6");
            assertThat(item.icon()).isEqualTo("target");
            assertThat(item.levels()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("fromCreate")
    class FromCreate {

        @Test
        @DisplayName("creates entity from request")
        void createsEntity() {
            var req = new CreateCourseRequest("COMU", "Comunicación", null, 4, 5, true, null, null, List.of());
            var entity = mapper.fromCreate(req);

            assertThat(entity.getCode()).isEqualTo("COMU");
            assertThat(entity.getName()).isEqualTo("Comunicación");
            assertThat(entity.getCredits()).isEqualTo(4);
            assertThat(entity.getIsActive()).isTrue();
            assertThat(entity.getIcon()).isEqualTo("book-open");
            assertThat(entity.getColor()).isEqualTo("#DC2626");
        }

        @Test
        @DisplayName("defaults isActive to true when null")
        void defaultsIsActive() {
            var req = new CreateCourseRequest("X", "X", null, null, null, null, null, null, List.of());
            var entity = mapper.fromCreate(req);
            assertThat(entity.getIsActive()).isTrue();
            assertThat(entity.getIcon()).isEqualTo(CourseVisualDefaults.DEFAULT_ICON);
            assertThat(entity.getColor()).isEqualTo(CourseVisualDefaults.DEFAULT_COLOR);
        }

        @Test
        @DisplayName("respects explicit color and icon")
        void explicitVisual() {
            var req = new CreateCourseRequest(
                    "MAT", "Mat", null, null, null, true, "#111111", "globe", List.of());
            var entity = mapper.fromCreate(req);
            assertThat(entity.getColor()).isEqualTo("#111111");
            assertThat(entity.getIcon()).isEqualTo("globe");
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("updates only non-null fields")
        void partialUpdate() {
            var patch = new UpdateCourseRequest(null, "Updated name", null, null, null, null, null, null);
            mapper.applyUpdate(patch, course);
            assertThat(course.getName()).isEqualTo("Updated name");
            assertThat(course.getCode()).isEqualTo("MAT");
            assertThat(course.getColor()).isEqualTo("#3B82F6");
        }

        @Test
        @DisplayName("blank description clears to null")
        void blankDescription() {
            var patch = new UpdateCourseRequest(null, null, "  ", null, null, null, null, null);
            mapper.applyUpdate(patch, course);
            assertThat(course.getDescription()).isNull();
        }

        @Test
        @DisplayName("all-null patch does nothing")
        void noop() {
            var patch = new UpdateCourseRequest(null, null, null, null, null, null, null, null);
            mapper.applyUpdate(patch, course);
            assertThat(course.getCode()).isEqualTo("MAT");
        }

        @Test
        @DisplayName("updates color and icon when provided")
        void updateVisual() {
            var patch = new UpdateCourseRequest(null, null, null, null, null, null, "#ABCDEF", "layers");
            mapper.applyUpdate(patch, course);
            assertThat(course.getColor()).isEqualTo("#ABCDEF");
            assertThat(course.getIcon()).isEqualTo("layers");
        }
    }
}
