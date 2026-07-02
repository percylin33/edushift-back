package com.edushift.modules.students.enrollments.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnrollmentDtoTest {

    @Test
    @DisplayName("EnrollmentResponse carries full projection + active flag")
    void enrollmentResponse() {
        UUID id = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID yearId = UUID.randomUUID();
        var r = new EnrollmentResponse(id, studentId, "Ana Diaz", "12345678",
                sectionId, "Section A", yearId, "2026",
                LocalDate.of(2026, 3, 1), null, StudentEnrollmentStatus.ACTIVE, true,
                "Welcome!", Instant.now(), Instant.now());
        assertThat(r.publicUuid()).isEqualTo(id);
        assertThat(r.studentFullName()).isEqualTo("Ana Diaz");
        assertThat(r.active()).isTrue();
        assertThat(r.notes()).isEqualTo("Welcome!");
    }

    @Test
    @DisplayName("EnrollmentListItem drops audit timestamps")
    void enrollmentListItem() {
        UUID id = UUID.randomUUID();
        var item = new EnrollmentListItem(id, UUID.randomUUID(), "Ana",
                UUID.randomUUID(), "Section A", UUID.randomUUID(), "2026",
                LocalDate.of(2026, 3, 1), null, StudentEnrollmentStatus.ACTIVE, true);
        assertThat(item.publicUuid()).isEqualTo(id);
        assertThat(item.active()).isTrue();
    }

    @Test
    @DisplayName("CreateEnrollmentRequest stores required UUIDs + date + notes")
    void createEnrollment() {
        var req = new CreateEnrollmentRequest(UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 3, 1), "transferred");
        assertThat(req.notes()).isEqualTo("transferred");
        assertThat(req.enrolledAt()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("WithdrawEnrollmentRequest stores terminal status + date")
    void withdrawEnrollment() {
        var req = new WithdrawEnrollmentRequest(StudentEnrollmentStatus.WITHDRAWN,
                LocalDate.of(2026, 7, 1));
        assertThat(req.status()).isEqualTo(StudentEnrollmentStatus.WITHDRAWN);
        assertThat(req.withdrawnAt()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    @DisplayName("SectionStudentRosterItem carries per-student roster projection")
    void sectionRoster() {
        UUID enrollmentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        var item = new SectionStudentRosterItem(enrollmentId, studentId, "Ana Diaz",
                DocumentType.DNI, "12345678", "ana@acme.test",
                LocalDate.of(2026, 3, 1), null, StudentEnrollmentStatus.ACTIVE, true);
        assertThat(item.enrollmentPublicUuid()).isEqualTo(enrollmentId);
        assertThat(item.studentFullName()).isEqualTo("Ana Diaz");
    }

    @Test
    @DisplayName("StudentEnrollmentStatus.isTerminal for non-ACTIVE")
    void statusIsTerminal() {
        assertThat(StudentEnrollmentStatus.ACTIVE.isTerminal()).isFalse();
        assertThat(StudentEnrollmentStatus.WITHDRAWN.isTerminal()).isTrue();
        assertThat(StudentEnrollmentStatus.TRANSFERRED.isTerminal()).isTrue();
        assertThat(StudentEnrollmentStatus.GRADUATED.isTerminal()).isTrue();
    }
}