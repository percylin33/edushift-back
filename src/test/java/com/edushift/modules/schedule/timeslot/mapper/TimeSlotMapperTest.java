package com.edushift.modules.schedule.timeslot.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.schedule.timeslot.dto.CreateTimeSlotRequest;
import com.edushift.modules.schedule.timeslot.dto.UpdateTimeSlotRequest;
import com.edushift.modules.schedule.timeslot.entity.TimeSlot;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.entity.Teacher;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TimeSlotMapperTest {

    private final TimeSlotMapper mapper = new TimeSlotMapper();

    private TeacherAssignment assignment;
    private Teacher teacher;
    private Course course;
    private Section section;
    private AcademicPeriod period;
    private TimeSlot slot;

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

        slot = new TimeSlot();
        slot.setPublicUuid(UUID.randomUUID());
        slot.setTeacherAssignment(assignment);
        slot.setDayOfWeek((short) 1);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(9, 0));
        slot.setClassroom("101");
        slot.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        slot.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all fields with assignment ref")
        void mapsAllFields() {
            var resp = mapper.toResponse(slot);
            assertThat(resp.publicUuid()).isEqualTo(slot.getPublicUuid());
            assertThat(resp.dayOfWeek()).isEqualTo((short) 1);
            assertThat(resp.classroom()).isEqualTo("101");
            assertThat(resp.assignment().teacher().firstName()).isEqualTo("Juan");
            assertThat(resp.assignment().course().code()).isEqualTo("MAT");
            assertThat(resp.assignment().section().name()).isEqualTo("A");
            assertThat(resp.assignment().period().name()).isEqualTo("I Bimestre");
        }

        @Test
        @DisplayName("handles null assignment")
        void nullAssignment() {
            slot.setTeacherAssignment(null);
            var resp = mapper.toResponse(slot);
            assertThat(resp.assignment()).isNull();
        }
    }

    @Nested
    @DisplayName("toListItem")
    class ToListItem {

        @Test
        @DisplayName("maps without assignment ref")
        void mapsFields() {
            var item = mapper.toListItem(slot);
            assertThat(item.publicUuid()).isEqualTo(slot.getPublicUuid());
            assertThat(item.dayOfWeek()).isEqualTo((short) 1);
        }
    }

    @Nested
    @DisplayName("toTeacherScheduleItem")
    class ToTeacherScheduleItem {

        @Test
        @DisplayName("maps with course/section/period, teacher is null")
        void mapsFields() {
            var item = mapper.toTeacherScheduleItem(slot);
            assertThat(item.slotPublicUuid()).isEqualTo(slot.getPublicUuid());
            assertThat(item.teacher()).isNull();
            assertThat(item.course().code()).isEqualTo("MAT");
            assertThat(item.section().name()).isEqualTo("A");
            assertThat(item.period().name()).isEqualTo("I Bimestre");
        }
    }

    @Nested
    @DisplayName("toSectionScheduleItem")
    class ToSectionScheduleItem {

        @Test
        @DisplayName("maps with teacher/course/period, section is null")
        void mapsFields() {
            var item = mapper.toSectionScheduleItem(slot);
            assertThat(item.slotPublicUuid()).isEqualTo(slot.getPublicUuid());
            assertThat(item.section()).isNull();
            assertThat(item.teacher().firstName()).isEqualTo("Juan");
            assertThat(item.course().code()).isEqualTo("MAT");
        }
    }

    @Nested
    @DisplayName("fromCreate")
    class FromCreate {

        @Test
        @DisplayName("creates entity from request")
        void createsEntity() {
            var req = new CreateTimeSlotRequest((short) 2,
                LocalTime.of(9, 0), LocalTime.of(10, 0), "102");
            var entity = mapper.fromCreate(req, assignment);

            assertThat(entity.getTeacherAssignment()).isEqualTo(assignment);
            assertThat(entity.getDayOfWeek()).isEqualTo((short) 2);
            assertThat(entity.getClassroom()).isEqualTo("102");
        }

        @Test
        @DisplayName("blank classroom becomes null")
        void blankClassroom() {
            var req = new CreateTimeSlotRequest((short) 3,
                LocalTime.of(10, 0), LocalTime.of(11, 0), "  ");
            var entity = mapper.fromCreate(req, assignment);
            assertThat(entity.getClassroom()).isNull();
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("updates provided fields")
        void partialUpdate() {
            var patch = new UpdateTimeSlotRequest(null,
                LocalTime.of(8, 30), LocalTime.of(9, 30), null);
            mapper.applyUpdate(patch, slot);

            assertThat(slot.getStartTime()).isEqualTo(LocalTime.of(8, 30));
            assertThat(slot.getEndTime()).isEqualTo(LocalTime.of(9, 30));
            assertThat(slot.getDayOfWeek()).isEqualTo((short) 1);
        }

        @Test
        @DisplayName("blank classroom becomes null")
        void blankClassroom() {
            var patch = new UpdateTimeSlotRequest(null, null, null, "  ");
            mapper.applyUpdate(patch, slot);
            assertThat(slot.getClassroom()).isNull();
        }
    }
}
