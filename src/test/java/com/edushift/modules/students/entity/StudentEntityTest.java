package com.edushift.modules.students.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudentEntityTest {

    @Test
    @DisplayName("defaults: gender=NOT_SPECIFIED, enrollmentStatus=PENDING, empty metadata")
    void defaults() {
        var s = new Student();
        assertThat(s.getGender()).isEqualTo(Gender.NOT_SPECIFIED);
        assertThat(s.getEnrollmentStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(s.getMetadata()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("fullName joins the three name parts; handles null parts")
    void fullName() {
        var s = new Student();
        assertThat(s.fullName()).isEmpty();
        s.setFirstName("Maria");
        s.setLastName("Lopez");
        assertThat(s.fullName()).isEqualTo("Maria Lopez");
        s.setSecondLastName("Gomez");
        assertThat(s.fullName()).isEqualTo("Maria Lopez Gomez");
        s.setFirstName(null);
        assertThat(s.fullName()).isEqualTo("Lopez Gomez");
    }

    @Test
    @DisplayName("markDeleted sets deletedAt; restore clears it")
    void lifecycle() {
        var s = new Student();
        s.markDeleted();
        assertThat(s.getDeletedAt()).isNotNull();
        assertThat(s.isDeleted()).isTrue();
        s.restore();
        assertThat(s.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("fields round-trip")
    void fields() {
        var s = new Student();
        s.setPublicUuid(UUID.randomUUID());
        s.setDocumentType(DocumentType.DNI);
        s.setDocumentNumber("12345678");
        s.setFirstName("Ana");
        s.setLastName("Diaz");
        s.setSecondLastName("Perez");
        s.setBirthDate(LocalDate.of(2015, 3, 1));
        s.setGender(Gender.FEMALE);
        s.setEmail("ana@acme.test");
        s.setPhone("+51 999");
        s.setAddress("Av. Test 123");
        s.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
        s.setEnrollmentDate(LocalDate.of(2026, 1, 1));
        s.setUserId(UUID.randomUUID());
        s.setMetadata(new HashMap<>());

        assertThat(s.getPublicUuid()).isNotNull();
        assertThat(s.getDocumentNumber()).isEqualTo("12345678");
        assertThat(s.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(s.getMetadata()).isEmpty();
    }
}