package com.edushift.modules.students.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.entity.DocumentType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuardianEntityTest {

    @Test
    @DisplayName("fullName combines first + last; handles null parts")
    void fullName() {
        var g = new Guardian();
        assertThat(g.fullName()).isEmpty();
        g.setFirstName("Juan");
        g.setLastName("Perez");
        assertThat(g.fullName()).isEqualTo("Juan Perez");
        g.setFirstName(null);
        assertThat(g.fullName()).isEqualTo("Perez");
    }

    @Test
    @DisplayName("markDeleted + restore lifecycle")
    void lifecycle() {
        var g = new Guardian();
        g.markDeleted();
        assertThat(g.getDeletedAt()).isNotNull();
        assertThat(g.isDeleted()).isTrue();
        g.restore();
        assertThat(g.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("fields round-trip")
    void fields() {
        var g = new Guardian();
        g.setPublicUuid(UUID.randomUUID());
        g.setDocumentType(DocumentType.DNI);
        g.setDocumentNumber("12345678");
        g.setFirstName("Ana");
        g.setLastName("Diaz");
        g.setEmail("ana@acme.test");
        g.setPhone("+51 999");
        g.setOccupation("Engineer");
        g.setUserId(UUID.randomUUID());
        assertThat(g.getDocumentType()).isEqualTo(DocumentType.DNI);
        assertThat(g.getOccupation()).isEqualTo("Engineer");
    }
}