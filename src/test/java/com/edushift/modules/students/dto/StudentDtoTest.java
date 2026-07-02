package com.edushift.modules.students.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.entity.BulkImportJob;
import com.edushift.modules.students.entity.BulkImportJobType;
import com.edushift.modules.students.entity.BulkImportStatus;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.RelationshipType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudentDtoTest {

    @Test
    @DisplayName("StudentListItem carries the lean columns")
    void studentListItem() {
        UUID id = UUID.randomUUID();
        var item = new StudentListItem(id, DocumentType.DNI, "12345678", "Ana", "Diaz",
                "Ana Diaz", "ana@acme.test", EnrollmentStatus.ENROLLED, LocalDate.of(2026, 1, 1));
        assertThat(item.publicUuid()).isEqualTo(id);
        assertThat(item.fullName()).isEqualTo("Ana Diaz");
        assertThat(item.enrollmentStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    @DisplayName("StudentResponse carries the full admin projection")
    void studentResponse() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var r = new StudentResponse(id, DocumentType.DNI, "12345678", "Ana", "Diaz", "Perez",
                "Ana Diaz Perez", LocalDate.of(2015, 3, 1), Gender.FEMALE,
                "ana@acme.test", "+51 999", "Av. Test",
                EnrollmentStatus.ENROLLED, LocalDate.of(2026, 1, 1),
                userId, Map.of("bloodType", "O+"),
                Instant.now(), Instant.now());
        assertThat(r.publicUuid()).isEqualTo(id);
        assertThat(r.secondLastName()).isEqualTo("Perez");
        assertThat(r.metadata()).containsEntry("bloodType", "O+");
        assertThat(r.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("CreateStudentRequest stores the full create payload")
    void createStudentRequest() {
        var req = new CreateStudentRequest(DocumentType.DNI, "12345678", "Ana", "Diaz",
                "Perez", LocalDate.of(2015, 3, 1), Gender.FEMALE, "ana@acme.test",
                "+51 999", "Av. Test", EnrollmentStatus.ENROLLED,
                LocalDate.of(2026, 1, 1), Map.of("bloodType", "O+"));
        assertThat(req.documentType()).isEqualTo(DocumentType.DNI);
        assertThat(req.firstName()).isEqualTo("Ana");
        assertThat(req.gender()).isEqualTo(Gender.FEMALE);
        assertThat(req.metadata()).containsEntry("bloodType", "O+");
    }

    @Test
    @DisplayName("UpdateStudentRequest.isEmpty true when every field is null")
    void updateStudentIsEmpty() {
        assertThat(new UpdateStudentRequest(null, null, null, null, null, null, null, null, null,
                null, null, null, null).isEmpty()).isTrue();
        assertThat(new UpdateStudentRequest(DocumentType.DNI, null, null, null, null, null, null,
                null, null, null, null, null, null).isEmpty()).isFalse();
    }

    @Test
    @DisplayName("StudentListFilters.empty produces all-null filters")
    void studentListFilters() {
        var f = StudentListFilters.empty();
        assertThat(f.search()).isNull();
        assertThat(f.enrollmentStatus()).isNull();
        assertThat(f.currentSectionPublicUuid()).isNull();
    }

    @Test
    @DisplayName("AddGuardianRequest stores required + optional profile fields")
    void addGuardianRequest() {
        var req = new AddGuardianRequest(DocumentType.DNI, "12345678", "Ana", "Diaz",
                "ana@acme.test", "+51 999", "Engineer", RelationshipType.MOTHER, true, false);
        assertThat(req.documentType()).isEqualTo(DocumentType.DNI);
        assertThat(req.relationship()).isEqualTo(RelationshipType.MOTHER);
        assertThat(req.isPrimaryContact()).isTrue();
        assertThat(req.canPickupStudent()).isFalse();
    }

    @Test
    @DisplayName("UpdateGuardianLinkRequest.isEmpty with all nulls")
    void updateGuardianLinkIsEmpty() {
        assertThat(new UpdateGuardianLinkRequest(null, null, null).isEmpty()).isTrue();
        assertThat(new UpdateGuardianLinkRequest(RelationshipType.MOTHER, null, null).isEmpty()).isFalse();
        assertThat(new UpdateGuardianLinkRequest(null, Boolean.TRUE, null).isEmpty()).isFalse();
    }

    @Test
    @DisplayName("GuardianResponse exposes composite projection")
    void guardianResponse() {
        UUID linkId = UUID.randomUUID();
        UUID guardId = UUID.randomUUID();
        var r = new GuardianResponse(linkId, guardId, DocumentType.DNI, "12345678",
                "Ana", "Diaz", "Ana Diaz", "ana@acme.test", "+51 999", "Engineer",
                RelationshipType.MOTHER, true, false);
        assertThat(r.linkPublicUuid()).isEqualTo(linkId);
        assertThat(r.guardianPublicUuid()).isEqualTo(guardId);
        assertThat(r.isPrimaryContact()).isTrue();
    }

    @Test
    @DisplayName("BulkImportJobResponse carries handle + counters + errors")
    void bulkImportJobResponse() {
        UUID id = UUID.randomUUID();
        var errors = List.of(new BulkImportJob.RowError(2, "ROW_INVALID", "x is blank"));
        var r = new BulkImportJobResponse(id, BulkImportJobType.STUDENTS, BulkImportStatus.PROCESSING,
                "students.xlsx", 1024L, 100, 25, 3, errors, null,
                Instant.now(), null, Instant.now());
        assertThat(r.publicUuid()).isEqualTo(id);
        assertThat(r.fileSizeBytes()).isEqualTo(1024L);
        assertThat(r.errors()).hasSize(1);
        assertThat(r.errors().get(0).row()).isEqualTo(2);
    }
}