package com.edushift.modules.teachers.assignments.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.period.entity.PeriodType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssignmentDtoTest {

    @Test
    @DisplayName("AssignmentResponse exposes denormalised fields + active flag")
    void assignmentResponse() {
        UUID id = UUID.randomUUID();
        var r = new AssignmentResponse(id, UUID.randomUUID(), "Ana Diaz",
                UUID.randomUUID(), "Section A", UUID.randomUUID(), "MAT", "Matemática",
                UUID.randomUUID(), PeriodType.BIMESTRE, 1, "B1 2026",
                UUID.randomUUID(), "2026", Instant.now(), null, true, "notes",
                Instant.now(), Instant.now());
        assertThat(r.publicUuid()).isEqualTo(id);
        assertThat(r.teacherFullName()).isEqualTo("Ana Diaz");
        assertThat(r.courseCode()).isEqualTo("MAT");
        assertThat(r.active()).isTrue();
        assertThat(r.unassignedAt()).isNull();
    }

    @Test
    @DisplayName("AssignmentListItem drops audit timestamps")
    void assignmentListItem() {
        var item = new AssignmentListItem(UUID.randomUUID(), UUID.randomUUID(), "Ana",
                UUID.randomUUID(), "Section A", UUID.randomUUID(), "MAT", "Matemática",
                UUID.randomUUID(), PeriodType.BIMESTRE, 1, Instant.now(), null, true);
        assertThat(item.teacherFullName()).isEqualTo("Ana");
        assertThat(item.courseCode()).isEqualTo("MAT");
    }

    @Test
    @DisplayName("CreateAssignmentRequest stores the three UUIDs + notes")
    void createAssignment() {
        var req = new CreateAssignmentRequest(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "transfer note");
        assertThat(req.notes()).isEqualTo("transfer note");
    }

    @Test
    @DisplayName("SectionTeacherItem reverse-view projection")
    void sectionTeacherItem() {
        var item = new SectionTeacherItem(UUID.randomUUID(), UUID.randomUUID(), "Ana",
                "ana@acme.test", UUID.randomUUID(), "MAT", "Matemática",
                UUID.randomUUID(), PeriodType.BIMESTRE, 1, Instant.now());
        assertThat(item.teacherEmail()).isEqualTo("ana@acme.test");
        assertThat(item.courseCode()).isEqualTo("MAT");
    }
}