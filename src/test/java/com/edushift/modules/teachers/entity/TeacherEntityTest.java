package com.edushift.modules.teachers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeacherEntityTest {

    @Test
    @DisplayName("defaults: gender=NOT_SPECIFIED, employmentStatus=ACTIVE, empty collections")
    void defaults() {
        var t = new Teacher();
        assertThat(t.getGender()).isEqualTo(Gender.NOT_SPECIFIED);
        assertThat(t.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(t.getSpecializations()).isNotNull().isEmpty();
        assertThat(t.getMetadata()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("fullName joins first + last + secondLast")
    void fullName() {
        var t = new Teacher();
        assertThat(t.fullName()).isEmpty();
        t.setFirstName("Juan");
        t.setLastName("Perez");
        t.setSecondLastName("Lopez");
        assertThat(t.fullName()).isEqualTo("Juan Perez Lopez");
        t.setFirstName(null);
        assertThat(t.fullName()).isEqualTo("Perez Lopez");
    }

    @Test
    @DisplayName("markDeleted + restore lifecycle")
    void lifecycle() {
        var t = new Teacher();
        t.markDeleted();
        assertThat(t.getDeletedAt()).isNotNull();
        assertThat(t.isDeleted()).isTrue();
        t.restore();
        assertThat(t.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("fields round-trip")
    void fields() {
        var t = new Teacher();
        t.setPublicUuid(UUID.randomUUID());
        t.setDocumentType(DocumentType.DNI);
        t.setDocumentNumber("12345678");
        t.setFirstName("Ana");
        t.setLastName("Diaz");
        t.setSecondLastName("Perez");
        t.setBirthDate(LocalDate.of(1990, 1, 1));
        t.setGender(Gender.FEMALE);
        t.setEmail("ana@acme.test");
        t.setPhone("+51 999");
        t.setTitle("Licenciada");
        t.setSpecializations(new ArrayList<>(List.of("Matemática", "Física")));
        t.setHireDate(LocalDate.of(2020, 3, 1));
        t.setEmploymentStatus(EmploymentStatus.ON_LEAVE);
        t.setUserId(UUID.randomUUID());
        t.setMetadata(new HashMap<>());
        assertThat(t.getSpecializations()).containsExactly("Matemática", "Física");
        assertThat(t.getEmploymentStatus()).isEqualTo(EmploymentStatus.ON_LEAVE);
    }

    @Test
    @DisplayName("EmploymentStatus: fromName parsing + isAssignable only for ACTIVE")
    void employmentStatus() {
        assertThat(EmploymentStatus.fromName("ACTIVE")).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(EmploymentStatus.fromName(null)).isNull();
        assertThat(EmploymentStatus.fromName("X")).isNull();
        assertThat(EmploymentStatus.ACTIVE.isAssignable()).isTrue();
        assertThat(EmploymentStatus.ON_LEAVE.isAssignable()).isFalse();
        assertThat(EmploymentStatus.SUSPENDED.isAssignable()).isFalse();
    }
}