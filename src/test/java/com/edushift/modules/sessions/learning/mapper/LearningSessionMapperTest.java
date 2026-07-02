package com.edushift.modules.sessions.learning.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.sessions.learning.dto.CreateLearningSessionRequest;
import com.edushift.modules.sessions.learning.dto.SessionContentDto;
import com.edushift.modules.sessions.learning.dto.UpdateLearningSessionRequest;
import com.edushift.modules.sessions.learning.entity.LearningSession;
import com.edushift.modules.sessions.learning.entity.SessionContent;
import com.edushift.modules.sessions.learning.entity.SessionStatus;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.entity.Teacher;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LearningSessionMapperTest {

    private final LearningSessionMapper mapper = new LearningSessionMapper();

    private TeacherAssignment assignment;
    private Teacher teacher;
    private Course course;
    private Section section;
    private AcademicPeriod period;
    private Unit unit;
    private LearningSession session;
    private Competency competency;
    private Capacity capacity;

    @BeforeEach
    void setUp() {
        teacher = new Teacher();
        teacher.setPublicUuid(UUID.randomUUID());
        teacher.setFirstName("Juan");
        teacher.setLastName("Pérez");

        course = new Course();
        course.setPublicUuid(UUID.randomUUID());
        course.setCode("MAT");
        course.setName("Matemática");

        section = new Section();
        section.setPublicUuid(UUID.randomUUID());
        section.setName("A");

        period = new AcademicPeriod();
        period.setPublicUuid(UUID.randomUUID());
        period.setPeriodType(PeriodType.BIMESTRE);
        period.setOrdinal(1);
        period.setName("I Bimestre");

        assignment = new TeacherAssignment();
        assignment.setPublicUuid(UUID.randomUUID());
        assignment.setTeacher(teacher);
        assignment.setCourse(course);
        assignment.setSection(section);
        assignment.setAcademicPeriod(period);

        unit = new Unit();
        unit.setPublicUuid(UUID.randomUUID());
        unit.setName("Unidad 1");
        unit.setDisplayOrder(1);

        competency = new Competency();
        competency.setPublicUuid(UUID.randomUUID());
        competency.setCode("C1");
        competency.setName("Competencia 1");
        competency.setDisplayOrder(1);

        capacity = new Capacity();
        capacity.setPublicUuid(UUID.randomUUID());
        capacity.setCode("CAP1");
        capacity.setName("Capacidad 1");
        capacity.setDisplayOrder(1);
        capacity.setCompetency(competency);

        var content = new SessionContent();
        content.setObjective("Objective");
        content.setActivities(List.of("Activity 1"));
        content.setMaterials(List.of("Material 1"));

        session = new LearningSession();
        session.setPublicUuid(UUID.randomUUID());
        session.setVersion(0L);
        session.setTeacherAssignment(assignment);
        session.setUnit(unit);
        session.setTitle("Sesión 1");
        session.setObjective("Objetivo");
        session.setScheduledDate(LocalDate.of(2026, 3, 1));
        session.setDurationMinutes(45);
        session.setStatus(SessionStatus.PLANNED);
        session.setContent(content);
        session.setCompetencies(Set.of(competency));
        session.setCapacities(Set.of(capacity));
        session.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        session.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields with full hierarchy")
        void mapsAllFields() {
            var resp = mapper.toResponse(session);

            assertThat(resp.publicUuid()).isEqualTo(session.getPublicUuid());
            assertThat(resp.version()).isEqualTo(0L);
            assertThat(resp.title()).isEqualTo("Sesión 1");
            assertThat(resp.unit().name()).isEqualTo("Unidad 1");
            assertThat(resp.assignment().teacher().firstName()).isEqualTo("Juan");
            assertThat(resp.assignment().course().code()).isEqualTo("MAT");
            assertThat(resp.assignment().section().name()).isEqualTo("A");
            assertThat(resp.assignment().period().name()).isEqualTo("I Bimestre");
            assertThat(resp.status()).isEqualTo(SessionStatus.PLANNED);
            assertThat(resp.competencies()).hasSize(1);
            assertThat(resp.competencies().get(0).code()).isEqualTo("C1");
            assertThat(resp.capacities()).hasSize(1);
            assertThat(resp.capacities().get(0).competencyPublicUuid())
                .isEqualTo(competency.getPublicUuid());
        }

        @Test
        @DisplayName("handles null content")
        void nullContent() {
            session.setContent(null);
            var resp = mapper.toResponse(session);
            assertThat(resp.content()).isNull();
        }

        @Test
        @DisplayName("handles null competencies/capacities")
        void nullCollections() {
            session.setCompetencies(null);
            session.setCapacities(null);
            var resp = mapper.toResponse(session);
            assertThat(resp.competencies()).isEmpty();
            assertThat(resp.capacities()).isEmpty();
        }

        @Test
        @DisplayName("handles null assignment")
        void nullAssignment() {
            session.setTeacherAssignment(null);
            var resp = mapper.toResponse(session);
            assertThat(resp.assignment()).isNull();
        }
    }

    @Nested
    @DisplayName("toListItem")
    class ToListItem {

        @Test
        @DisplayName("maps with assignment and unit summaries")
        void mapsFields() {
            var item = mapper.toListItem(session);
            assertThat(item.title()).isEqualTo("Sesión 1");
            assertThat(item.assignment().teacherName()).isEqualTo("Juan Pérez");
            assertThat(item.assignment().courseCode()).isEqualTo("MAT");
            assertThat(item.assignment().sectionName()).isEqualTo("A");
            assertThat(item.unit().name()).isEqualTo("Unidad 1");
        }

        @Test
        @DisplayName("handles null teacher")
        void nullTeacher() {
            assignment.setTeacher(null);
            var item = mapper.toListItem(session);
            assertThat(item.assignment().teacherName()).isNull();
        }

        @Test
        @DisplayName("handles null assignment")
        void nullAssignment() {
            session.setTeacherAssignment(null);
            var item = mapper.toListItem(session);
            assertThat(item.assignment()).isNull();
        }

        @Test
        @DisplayName("handles null unit")
        void nullUnit() {
            session.setUnit(null);
            var item = mapper.toListItem(session);
            assertThat(item.unit()).isNull();
        }
    }

    @Nested
    @DisplayName("fromCreate")
    class FromCreate {

        @Test
        @DisplayName("creates entity from request")
        void createsEntity() {
            var dto = new SessionContentDto("Obj", List.of("Act"), List.of("Mat"), null);
            var req = new CreateLearningSessionRequest(assignment.getPublicUuid(),
                unit.getPublicUuid(), "Sesión 2", "Obj",
                LocalDate.of(2026, 3, 2), 50, dto,
                List.of(competency.getPublicUuid()),
                List.of(capacity.getPublicUuid()));
            var entity = mapper.fromCreate(req, assignment, unit);

            assertThat(entity.getTeacherAssignment()).isEqualTo(assignment);
            assertThat(entity.getUnit()).isEqualTo(unit);
            assertThat(entity.getTitle()).isEqualTo("Sesión 2");
            assertThat(entity.getDurationMinutes()).isEqualTo(50);
            assertThat(entity.getContent().getObjective()).isEqualTo("Obj");
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("updates provided fields")
        void partialUpdate() {
            var dto = new SessionContentDto("New Obj", List.of(), List.of(), "Obs");
            var patch = new UpdateLearningSessionRequest(null, "Updated",
                "New Obj", LocalDate.of(2026, 3, 5), 60, dto, null, null);
            mapper.applyUpdate(patch, session);

            assertThat(session.getTitle()).isEqualTo("Updated");
            assertThat(session.getObjective()).isEqualTo("New Obj");
            assertThat(session.getDurationMinutes()).isEqualTo(60);
            assertThat(session.getContent().getObservations()).isEqualTo("Obs");
        }

        @Test
        @DisplayName("blank objective becomes null")
        void blankObjective() {
            var patch = new UpdateLearningSessionRequest(null, null, "  ",
                null, null, null, null, null);
            mapper.applyUpdate(patch, session);
            assertThat(session.getObjective()).isNull();
        }
    }

    @Nested
    @DisplayName("SessionContent mapper")
    class ContentMapper {

        @Test
        @DisplayName("toEntityContent converts from DTO")
        void toEntityContent() {
            var dto = new SessionContentDto("Obj", List.of("A1"), List.of("M1"), "Obs");
            var entity = mapper.toEntityContent(dto);
            assertThat(entity.getObjective()).isEqualTo("Obj");
            assertThat(entity.getActivities()).containsExactly("A1");
        }

        @Test
        @DisplayName("toEntityContent returns null for null input")
        void toEntityContentNull() {
            assertThat(mapper.toEntityContent(null)).isNull();
        }
    }
}
