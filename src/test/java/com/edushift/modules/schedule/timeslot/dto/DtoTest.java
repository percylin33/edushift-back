package com.edushift.modules.schedule.timeslot.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TimeSlot DTOs")
class DtoTest {

    @Test
    @DisplayName("TimeSlotResponse — constructor + accessors")
    void timeSlotResponse() {
        var assignmentRef = new TimeSlotResponse.AssignmentRef(
            UUID.randomUUID(),
            new TimeSlotResponse.TeacherRef(UUID.randomUUID(), "Juan", "Pérez"),
            new TimeSlotResponse.CourseRef(UUID.randomUUID(), "MAT", "Mat"),
            new TimeSlotResponse.SectionRef(UUID.randomUUID(), "A"),
            new TimeSlotResponse.PeriodRef(UUID.randomUUID(), "BIMESTRE", 1, "I Bimestre"));
        var resp = new TimeSlotResponse(UUID.randomUUID(), assignmentRef,
            (short) 1, LocalTime.of(8, 0), LocalTime.of(9, 0), "101",
            Instant.now(), Instant.now());
        assertThat(resp.dayOfWeek()).isEqualTo((short) 1);
        assertThat(resp.classroom()).isEqualTo("101");
    }

    @Test
    @DisplayName("TimeSlotListItem — constructor + accessors")
    void timeSlotListItem() {
        var item = new TimeSlotListItem(UUID.randomUUID(), (short) 2,
            LocalTime.of(9, 0), LocalTime.of(10, 0), "102");
        assertThat(item.dayOfWeek()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("CreateTimeSlotRequest — constructor + accessors")
    void createTimeSlotRequest() {
        var req = new CreateTimeSlotRequest((short) 1,
            LocalTime.of(8, 0), LocalTime.of(9, 0), "101");
        assertThat(req.dayOfWeek()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("UpdateTimeSlotRequest — isEmpty checks")
    void updateTimeSlotRequest() {
        var empty = new UpdateTimeSlotRequest(null, null, null, null);
        assertThat(empty.isEmpty()).isTrue();

        var nonEmpty = new UpdateTimeSlotRequest((short) 2, null, null, null);
        assertThat(nonEmpty.isEmpty()).isFalse();
        assertThat(nonEmpty.dayOfWeek()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("ScheduleSlotItem — constructor + accessors")
    void scheduleSlotItem() {
        var teacherRef = new ScheduleSlotItem.TeacherRef(UUID.randomUUID(), "Juan", "Pérez");
        var courseRef = new ScheduleSlotItem.CourseRef(UUID.randomUUID(), "MAT", "Mat");
        var sectionRef = new ScheduleSlotItem.SectionRef(UUID.randomUUID(), "A");
        var periodRef = new ScheduleSlotItem.PeriodRef(UUID.randomUUID(), "BIMESTRE", 1, "I Bimestre");
        var item = new ScheduleSlotItem(UUID.randomUUID(), UUID.randomUUID(),
            (short) 1, LocalTime.of(8, 0), LocalTime.of(9, 0), "101",
            teacherRef, courseRef, sectionRef, periodRef);
        assertThat(item.dayOfWeek()).isEqualTo((short) 1);
        assertThat(item.teacher().firstName()).isEqualTo("Juan");
    }
}
