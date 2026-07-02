package com.edushift.modules.sessions.learning.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.sessions.learning.entity.SessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Learning Session DTOs")
class DtoTest {

    @Test
    @DisplayName("LearningSessionResponse — constructor + accessors")
    void learningSessionResponse() {
        var teacherRef = new LearningSessionResponse.TeacherRef(UUID.randomUUID(), "Juan", "Pérez");
        var courseRef = new LearningSessionResponse.CourseRef(UUID.randomUUID(), "MAT", "Mat");
        var sectionRef = new LearningSessionResponse.SectionRef(UUID.randomUUID(), "A");
        var periodRef = new LearningSessionResponse.PeriodRef(UUID.randomUUID(), "BIMESTRE", 1,
            "I Bimestre", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31));
        var assignmentRef = new LearningSessionResponse.AssignmentRef(
            UUID.randomUUID(), teacherRef, courseRef, sectionRef, periodRef);
        var unitRef = new LearningSessionResponse.UnitRef(UUID.randomUUID(), "U1", 1);
        var compRef = new LearningSessionResponse.CompetencyRef(UUID.randomUUID(), "C1", "Comp 1");
        var capRef = new LearningSessionResponse.CapacityRef(UUID.randomUUID(), "CAP1", "Cap 1",
            UUID.randomUUID());
        var content = new SessionContentDto("Obj", List.of("Act"), List.of("Mat"), null);

        var resp = new LearningSessionResponse(UUID.randomUUID(), 0L,
            assignmentRef, unitRef, "Sesión 1", "Obj",
            LocalDate.of(2026, 3, 1), 45, SessionStatus.PLANNED,
            content, List.of(compRef), List.of(capRef),
            null, null, null, Instant.now(), Instant.now());
        assertThat(resp.title()).isEqualTo("Sesión 1");
        assertThat(resp.status()).isEqualTo(SessionStatus.PLANNED);
        assertThat(resp.competencies()).hasSize(1);
        assertThat(resp.capacities()).hasSize(1);
    }

    @Test
    @DisplayName("LearningSessionListItem — constructor + accessors")
    void learningSessionListItem() {
        var assignmentSummary = new LearningSessionListItem.AssignmentSummary(
            UUID.randomUUID(), "Juan Pérez", "MAT", "A");
        var unitSummary = new LearningSessionListItem.UnitSummary(
            UUID.randomUUID(), "U1", 1);
        var item = new LearningSessionListItem(UUID.randomUUID(), 0L,
            "Sesión 1", LocalDate.of(2026, 3, 1), 45,
            SessionStatus.PLANNED, null, null, null,
            assignmentSummary, unitSummary,
            Instant.now(), Instant.now());
        assertThat(item.title()).isEqualTo("Sesión 1");
        assertThat(item.assignment().teacherName()).isEqualTo("Juan Pérez");
    }

    @Test
    @DisplayName("CreateLearningSessionRequest — constructor + accessors")
    void createLearningSessionRequest() {
        var content = new SessionContentDto("Obj", List.of("Act"), null, null);
        var req = new CreateLearningSessionRequest(UUID.randomUUID(), UUID.randomUUID(),
            "Sesión 1", "Obj", LocalDate.of(2026, 3, 1), 45, content,
            List.of(UUID.randomUUID()), List.of(UUID.randomUUID()));
        assertThat(req.title()).isEqualTo("Sesión 1");
        assertThat(req.durationMinutes()).isEqualTo(45);
    }

    @Test
    @DisplayName("UpdateLearningSessionRequest — isEmpty checks")
    void updateLearningSessionRequest() {
        var empty = new UpdateLearningSessionRequest(null, null, null, null, null, null, null, null);
        assertThat(empty.isEmpty()).isTrue();

        var nonEmpty = new UpdateLearningSessionRequest(UUID.randomUUID(), "Updated",
            null, null, null, null, null, null);
        assertThat(nonEmpty.isEmpty()).isFalse();
        assertThat(nonEmpty.title()).isEqualTo("Updated");
    }

    @Test
    @DisplayName("SessionContentDto — constructor + accessors")
    void sessionContentDto() {
        var dto = new SessionContentDto("Obj", List.of("Act"), List.of("Mat"), "Obs");
        assertThat(dto.objective()).isEqualTo("Obj");
        assertThat(dto.activities()).containsExactly("Act");
    }

    @Test
    @DisplayName("LifecycleRequest — constructor + accessors")
    void lifecycleRequest() {
        var req = new LifecycleRequest(5L, "reason");
        assertThat(req.version()).isEqualTo(5L);
        assertThat(req.reason()).isEqualTo("reason");
    }

    @Test
    @DisplayName("LearningSessionFilters — isEmpty checks")
    void learningSessionFilters() {
        var empty = new LearningSessionFilters(null, null, null, null, null, null, null);
        assertThat(empty.isEmpty()).isTrue();

        var nonEmpty = new LearningSessionFilters(UUID.randomUUID(), null, null, null, null, null, null);
        assertThat(nonEmpty.isEmpty()).isFalse();
    }
}
