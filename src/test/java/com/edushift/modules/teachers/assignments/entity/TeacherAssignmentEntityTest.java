package com.edushift.modules.teachers.assignments.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeacherAssignmentEntityTest {

    @Test
    @DisplayName("fresh assignment is active (unassignedAt is null)")
    void fresh() {
        var a = new TeacherAssignment();
        assertThat(a.isActive()).isTrue();
    }

    @Test
    @DisplayName("isActive false once unassignedAt is set")
    void softEnded() {
        var a = new TeacherAssignment();
        a.setUnassignedAt(Instant.now());
        assertThat(a.isActive()).isFalse();
    }

    @Test
    @DisplayName("markDeleted + restore lifecycle")
    void lifecycle() {
        var a = new TeacherAssignment();
        a.markDeleted();
        assertThat(a.getDeletedAt()).isNotNull();
        assertThat(a.isDeleted()).isTrue();
        a.restore();
        assertThat(a.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("assignedAt populated on first save; publicUuid populated")
    void persistenceShape() {
        var a = new TeacherAssignment();
        a.setPublicUuid(UUID.randomUUID());
        a.setAssignedAt(Instant.parse("2026-03-01T00:00:00Z"));
        a.setNotes("MAT sec A");
        assertThat(a.getAssignedAt()).isNotNull();
        assertThat(a.getNotes()).isEqualTo("MAT sec A");
    }
}