package com.edushift.modules.teachers.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeacherDtoTest {

    @Test
    @DisplayName("TeacherResponse carries full projection")
    void teacherResponse() {
        UUID id = UUID.randomUUID();
        UUID userPub = UUID.randomUUID();
        var r = new TeacherResponse(id, DocumentType.DNI, "12345678", "Ana", "Diaz", "Perez",
                LocalDate.of(1990, 1, 1), Gender.FEMALE, "ana@acme.test", "+51 999",
                "Lic.", List.of("Matemática", "Física"),
                LocalDate.of(2020, 3, 1), EmploymentStatus.ACTIVE, userPub,
                Map.of("certification", "PhD"), Instant.now(), Instant.now());
        assertThat(r.publicUuid()).isEqualTo(id);
        assertThat(r.userPublicUuid()).isEqualTo(userPub);
        assertThat(r.specializations()).containsExactly("Matemática", "Física");
        assertThat(r.metadata()).containsEntry("certification", "PhD");
    }

    @Test
    @DisplayName("TeacherListItem exposes hasUserAccount flag")
    void teacherListItem() {
        var item = new TeacherListItem(UUID.randomUUID(), DocumentType.DNI, "12345678",
                "Ana", "Diaz", "Perez", "ana@acme.test", "Lic.",
                List.of("Mat"), EmploymentStatus.ACTIVE, true);
        assertThat(item.hasUserAccount()).isTrue();
        assertThat(item.specializations()).containsExactly("Mat");
    }

    @Test
    @DisplayName("CreateTeacherRequest stores the create payload")
    void createTeacherRequest() {
        var req = new CreateTeacherRequest(DocumentType.DNI, "12345678", "Ana", "Diaz",
                "Perez", LocalDate.of(1990, 1, 1), Gender.FEMALE, "ana@acme.test",
                "+51 999", "Lic.", List.of("Mat"), LocalDate.of(2020, 3, 1),
                EmploymentStatus.ACTIVE, Map.of());
        assertThat(req.documentType()).isEqualTo(DocumentType.DNI);
        assertThat(req.firstName()).isEqualTo("Ana");
        assertThat(req.specializations()).containsExactly("Mat");
    }

    @Test
    @DisplayName("UpdateTeacherRequest.isEmpty")
    void updateTeacherIsEmpty() {
        assertThat(new UpdateTeacherRequest(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null).isEmpty()).isTrue();
        assertThat(new UpdateTeacherRequest(DocumentType.DNI, null, null, null, null, null, null,
                null, null, null, null, null, null, null).isEmpty()).isFalse();
    }

    @Test
    @DisplayName("LinkTeacherUserRequest stores a userPublicUuid")
    void linkTeacherUser() {
        var req = new LinkTeacherUserRequest(UUID.randomUUID());
        assertThat(req.userPublicUuid()).isNotNull();
    }

    @Test
    @DisplayName("InviteTeacherResponse stores invitation handle + teacher handle")
    void inviteTeacherResponse() {
        UUID invitation = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();
        var r = new InviteTeacherResponse(invitation, "opaque.token", Instant.now(), teacher,
                "ana@acme.test");
        assertThat(r.invitationPublicUuid()).isEqualTo(invitation);
        assertThat(r.invitationToken()).isEqualTo("opaque.token");
        assertThat(r.teacherPublicUuid()).isEqualTo(teacher);
        assertThat(r.email()).isEqualTo("ana@acme.test");
    }
}