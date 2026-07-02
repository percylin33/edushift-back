package com.edushift.modules.students.enrollments.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lightweight mapper test: the full denormalised projection requires
 * Section / AcademicYear / Student entity references. We assert that
 * the mapper correctly threads those into the response when they're
 * populated, and that {@code active} reflects the status enum.
 */
class StudentEnrollmentMapperTest {

    @Test
    @DisplayName("toResponse: active flag mirrors status")
    void toResponse() {
        var student = new Student();
        student.setPublicUuid(UUID.randomUUID());
        student.setFirstName("Ana");
        student.setLastName("Diaz");
        student.setDocumentNumber("12345678");

        var section = new Section();
        section.setPublicUuid(UUID.randomUUID());
        section.setName("Section A");

        var year = new AcademicYear();
        year.setPublicUuid(UUID.randomUUID());
        year.setName("2026");

        var enrollment = new StudentEnrollment();
        enrollment.setPublicUuid(UUID.randomUUID());
        enrollment.setStudent(student);
        enrollment.setSection(section);
        enrollment.setAcademicYear(year);
        enrollment.setEnrolledAt(LocalDate.of(2026, 3, 1));
        enrollment.setStatus(StudentEnrollmentStatus.ACTIVE);
        enrollment.setNotes("notes");
        enrollment.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
        enrollment.setUpdatedAt(Instant.parse("2026-03-02T00:00:00Z"));

        var mapper = new StudentEnrollmentMapper();
        var resp = mapper.toResponse(enrollment);
        assertThat(resp.publicUuid()).isEqualTo(enrollment.getPublicUuid());
        assertThat(resp.studentFullName()).isEqualTo("Ana Diaz");
        assertThat(resp.studentDocumentNumber()).isEqualTo("12345678");
        assertThat(resp.sectionName()).isEqualTo("Section A");
        assertThat(resp.status()).isEqualTo(StudentEnrollmentStatus.ACTIVE);
        assertThat(resp.active()).isTrue();
    }

    @Test
    @DisplayName("toListItem: drops audit timestamps but keeps the active flag")
    void toListItem() {
        var student = new Student();
        student.setPublicUuid(UUID.randomUUID());
        student.setFirstName("Ana");
        student.setLastName("Diaz");
        var section = new Section();
        section.setPublicUuid(UUID.randomUUID());
        section.setName("Section A");
        var year = new AcademicYear();
        year.setPublicUuid(UUID.randomUUID());
        year.setName("2026");
        var enrollment = new StudentEnrollment();
        enrollment.setPublicUuid(UUID.randomUUID());
        enrollment.setStudent(student);
        enrollment.setSection(section);
        enrollment.setAcademicYear(year);
        enrollment.setEnrolledAt(LocalDate.of(2026, 3, 1));
        enrollment.setStatus(StudentEnrollmentStatus.WITHDRAWN);

        var resp = new StudentEnrollmentMapper().toListItem(enrollment);
        assertThat(resp.active()).isFalse();
        assertThat(resp.status()).isEqualTo(StudentEnrollmentStatus.WITHDRAWN);
        assertThat(resp.studentFullName()).isEqualTo("Ana Diaz");
    }

    @Test
    @DisplayName("toRosterItem: exposes DocumentType + documentNumber + email")
    void toRosterItem() {
        var student = new Student();
        student.setPublicUuid(UUID.randomUUID());
        student.setFirstName("Ana");
        student.setLastName("Diaz");
        student.setDocumentType(com.edushift.modules.students.entity.DocumentType.DNI);
        student.setDocumentNumber("12345678");
        student.setEmail("ana@acme.test");
        var section = new Section();
        section.setPublicUuid(UUID.randomUUID());
        section.setName("Section A");
        var year = new AcademicYear();
        year.setPublicUuid(UUID.randomUUID());
        year.setName("2026");
        var enrollment = new StudentEnrollment();
        enrollment.setPublicUuid(UUID.randomUUID());
        enrollment.setStudent(student);
        enrollment.setSection(section);
        enrollment.setAcademicYear(year);
        enrollment.setEnrolledAt(LocalDate.of(2026, 3, 1));
        enrollment.setStatus(StudentEnrollmentStatus.ACTIVE);

        var resp = new StudentEnrollmentMapper().toRosterItem(enrollment);
        assertThat(resp.studentFullName()).isEqualTo("Ana Diaz");
        assertThat(resp.documentType()).isEqualTo(com.edushift.modules.students.entity.DocumentType.DNI);
        assertThat(resp.documentNumber()).isEqualTo("12345678");
        assertThat(resp.email()).isEqualTo("ana@acme.test");
        assertThat(resp.active()).isTrue();
    }
}