package com.edushift.modules.academic.course.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Course DTOs")
class DtoTest {

    @Test
    @DisplayName("CourseResponse — constructor + accessors")
    void courseResponse() {
        var levelRef = new CourseResponse.CourseLevelRef(UUID.randomUUID(), "PRIMARIA", "Primaria", 2);
        var resp = new CourseResponse(UUID.randomUUID(), "MAT", "Matemática", null, 4, 5, true,
            List.of(levelRef), Instant.now(), Instant.now());
        assertThat(resp.code()).isEqualTo("MAT");
        assertThat(resp.levels()).hasSize(1);
    }

    @Test
    @DisplayName("CourseListItem — constructor + accessors")
    void courseListItem() {
        var item = new CourseListItem(UUID.randomUUID(), "MAT", "Mat", 4, 5, true, List.of());
        assertThat(item.code()).isEqualTo("MAT");
    }

    @Test
    @DisplayName("CreateCourseRequest — constructor + accessors")
    void createCourseRequest() {
        var req = new CreateCourseRequest("MAT", "Mat", null, 4, 5, true, List.of(UUID.randomUUID()));
        assertThat(req.code()).isEqualTo("MAT");
        assertThat(req.credits()).isEqualTo(4);
    }

    @Test
    @DisplayName("UpdateCourseRequest — constructor + accessors")
    void updateCourseRequest() {
        var req = new UpdateCourseRequest("MAT2", null, null, 5, null, false);
        assertThat(req.code()).isEqualTo("MAT2");
        assertThat(req.isActive()).isFalse();
    }

    @Test
    @DisplayName("UpdateCourseLevelsRequest — constructor + accessors")
    void updateCourseLevelsRequest() {
        var uuids = List.of(UUID.randomUUID(), UUID.randomUUID());
        var req = new UpdateCourseLevelsRequest(uuids);
        assertThat(req.levelPublicUuids()).hasSize(2);
    }
}
