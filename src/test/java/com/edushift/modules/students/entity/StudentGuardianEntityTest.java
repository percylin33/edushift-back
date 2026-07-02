package com.edushift.modules.students.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.entity.RelationshipType;
import com.edushift.modules.students.entity.StudentGuardian;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudentGuardianEntityTest {

    @Test
    @DisplayName("defaults: not primary, not pickup")
    void defaults() {
        var sg = new StudentGuardian();
        assertThat(sg.isPrimaryContact()).isFalse();
        assertThat(sg.isCanPickupStudent()).isFalse();
    }

    @Test
    @DisplayName("markDeleted + restore lifecycle")
    void lifecycle() {
        var sg = new StudentGuardian();
        sg.markDeleted();
        assertThat(sg.getDeletedAt()).isNotNull();
        sg.restore();
        assertThat(sg.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("fields round-trip")
    void fields() {
        var sg = new StudentGuardian();
        sg.setPublicUuid(UUID.randomUUID());
        sg.setRelationship(RelationshipType.MOTHER);
        sg.setPrimaryContact(true);
        sg.setCanPickupStudent(true);
        assertThat(sg.getRelationship()).isEqualTo(RelationshipType.MOTHER);
        assertThat(sg.isPrimaryContact()).isTrue();
        assertThat(sg.isCanPickupStudent()).isTrue();
    }
}