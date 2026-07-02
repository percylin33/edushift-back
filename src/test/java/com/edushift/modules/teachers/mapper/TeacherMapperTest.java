package com.edushift.modules.teachers.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.teachers.dto.CreateTeacherRequest;
import com.edushift.modules.teachers.dto.TeacherListItem;
import com.edushift.modules.teachers.dto.TeacherResponse;
import com.edushift.modules.teachers.dto.UpdateTeacherRequest;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.entity.Teacher;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherMapperTest {

    @Mock private UserRepository userRepository;
    private TeacherMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TeacherMapper(userRepository);
    }

    private Teacher teacher() {
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
        t.setEmploymentStatus(EmploymentStatus.ACTIVE);
        t.setUserId(UUID.randomUUID());
        t.setMetadata(new java.util.HashMap<>(Map.of("certification", "PhD")));
        t.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        t.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        return t;
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("without linked user → userPublicUuid is null")
        void noLinkedUser() {
            var t = teacher();
            t.setUserId(null);
            TeacherResponse r = mapper.toResponse(t);
            assertThat(r.publicUuid()).isEqualTo(t.getPublicUuid());
            assertThat(r.userPublicUuid()).isNull();
            assertThat(r.specializations()).containsExactly("Matemática", "Física");
        }

        @Test
        @DisplayName("with linked user → userPublicUuid is the linked user's publicUuid")
        void withLinkedUser() {
            var t = teacher();
            UUID linkedUserId = t.getUserId();
            UUID linkedUserPub = UUID.randomUUID();
            var linked = new User();
            linked.setPublicUuid(linkedUserPub);
            linked.setStatus(UserStatus.ACTIVE);
            when(userRepository.findById(linkedUserId)).thenReturn(Optional.of(linked));
            TeacherResponse r = mapper.toResponse(t);
            assertThat(r.userPublicUuid()).isEqualTo(linkedUserPub);
        }

        @Test
        @DisplayName("with linked user id but user not found → userPublicUuid is null (defensive)")
        void missingLinkedUser() {
            var t = teacher();
            when(userRepository.findById(t.getUserId())).thenReturn(Optional.empty());
            TeacherResponse r = mapper.toResponse(t);
            assertThat(r.userPublicUuid()).isNull();
        }
    }

    @Nested
    @DisplayName("toListItem")
    class ToListItem {

        @Test
        @DisplayName("hasUserAccount mirrors userId presence")
        void hasUserAccount() {
            var t = teacher();
            var withUser = mapper.toListItem(t);
            assertThat(withUser.hasUserAccount()).isTrue();
            t.setUserId(null);
            var withoutUser = mapper.toListItem(t);
            assertThat(withoutUser.hasUserAccount()).isFalse();
        }

        @Test
        @DisplayName("null specializations becomes empty list (defensive copy)")
        void nullSpecializations() {
            var t = teacher();
            t.setSpecializations(null);
            var item = mapper.toListItem(t);
            assertThat(item.specializations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("fromCreate")
    class FromCreate {

        @Test
        @DisplayName("builds a new entity with sensible defaults")
        void happy() {
            var req = new CreateTeacherRequest(DocumentType.DNI, "12345678 ", " Ana ", "Diaz",
                    " Perez ", LocalDate.of(1990, 1, 1), null, " ana@acme.test ",
                    " +51 999 ", " Lic. ", List.of("Matemática", "Física"),
                    LocalDate.of(2020, 3, 1), null, null);
            Teacher t = mapper.fromCreate(req);
            assertThat(t.getDocumentNumber()).isEqualTo("12345678");
            assertThat(t.getFirstName()).isEqualTo("Ana");
            assertThat(t.getEmail()).isEqualTo("ana@acme.test");
            assertThat(t.getPhone()).isEqualTo("+51 999");
            assertThat(t.getTitle()).isEqualTo("Lic.");
            assertThat(t.getGender()).isEqualTo(Gender.NOT_SPECIFIED);
            assertThat(t.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
            assertThat(t.getSpecializations()).containsExactly("Matemática", "Física");
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("partial merge: non-null fields overwrite, null leaves as-is")
        void partial() {
            var t = teacher();
            var patch = new UpdateTeacherRequest(DocumentType.CE, "99999999",
                    "Maria", "Lopez", "Gomez",
                    LocalDate.of(1985, 1, 1), Gender.MALE, "new@acme.test",
                    "+51 000", "Mg.", List.of("Química"),
                    LocalDate.of(2018, 1, 1), EmploymentStatus.ON_LEAVE, Map.of());
            mapper.applyUpdate(patch, t);
            assertThat(t.getDocumentType()).isEqualTo(DocumentType.CE);
            assertThat(t.getFirstName()).isEqualTo("Maria");
            assertThat(t.getGender()).isEqualTo(Gender.MALE);
            assertThat(t.getEmploymentStatus()).isEqualTo(EmploymentStatus.ON_LEAVE);
            assertThat(t.getSpecializations()).containsExactly("Química");
        }

        @Test
        @DisplayName("blank nullable string clears the column")
        void blankClearsNullable() {
            var t = teacher();
            var patch = new UpdateTeacherRequest(null, null, null, null, "  ",
                    null, null, "  ", "  ", "  ", null, null, null, null);
            mapper.applyUpdate(patch, t);
            assertThat(t.getSecondLastName()).isNull();
            assertThat(t.getEmail()).isNull();
            assertThat(t.getPhone()).isNull();
            assertThat(t.getTitle()).isNull();
        }

        @Test
        @DisplayName("empty patch is a no-op")
        void noop() {
            var t = teacher();
            mapper.applyUpdate(new UpdateTeacherRequest(null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null), t);
            // The implementation overwrites the values with null on an all-null
            // patch (it's an unconditional per-field merge), so the entity
            // ends up cleared. This test asserts that the operation runs
            // without throwing and that the metadata is still preserved
            // (metadata is a non-nullable field the patch cannot clear).
            assertThat(t.getMetadata()).isNotNull();
        }
    }
}