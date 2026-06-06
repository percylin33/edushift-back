package com.edushift.modules.teachers.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.teachers.dto.CreateTeacherRequest;
import com.edushift.modules.teachers.dto.InviteTeacherResponse;
import com.edushift.modules.teachers.dto.LinkTeacherUserRequest;
import com.edushift.modules.teachers.dto.UpdateTeacherRequest;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.mapper.TeacherMapper;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.modules.users.dto.CreateInvitationRequest;
import com.edushift.modules.users.dto.InvitationResponse;
import com.edushift.modules.users.entity.InvitationStatus;
import com.edushift.modules.users.service.UserInvitationService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class TeacherServiceImplTest {

	@Mock private TeacherRepository teacherRepository;
	@Mock private UserRepository userRepository;
	@Mock private UserInvitationService invitationService;
	@Mock private TeacherAssignmentRepository assignmentRepository;
	@Mock private TeacherMapper mapper;

	@InjectMocks private TeacherServiceImpl service;

	private static final UUID TEACHER_PUBLIC = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
	private static final UUID TEACHER_INTERNAL = UUID.fromString("aaaaaaaa-2222-2222-2222-222222222222");
	private static final UUID USER_PUBLIC = UUID.fromString("bbbbbbbb-1111-1111-1111-111111111111");
	private static final UUID USER_INTERNAL = UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222");

	// =========================================================================
	// createTeacher
	// =========================================================================

	@Nested
	@DisplayName("createTeacher")
	class CreateTeacher {

		@Test
		@DisplayName("happy path — saves and returns the persisted projection")
		void happyPath() {
			when(teacherRepository.findByDocument(eq(DocumentType.DNI), eq("12345678")))
					.thenReturn(Optional.empty());
			when(teacherRepository.findByEmailIgnoreCase(eq("ada@acme.test")))
					.thenReturn(Optional.empty());

			Teacher built = teacher("Ada", "Lovelace", "12345678", "ada@acme.test", null);
			when(mapper.fromCreate(any(CreateTeacherRequest.class))).thenReturn(built);
			when(teacherRepository.saveAndFlush(any(Teacher.class)))
					.thenAnswer(inv -> inv.getArgument(0));
			when(mapper.toResponse(any(Teacher.class))).thenReturn(null);

			CreateTeacherRequest request = new CreateTeacherRequest(
					DocumentType.DNI, "12345678", "Ada", "Lovelace", null,
					null, Gender.FEMALE, "ada@acme.test", null,
					"Mg.", List.of("Matematica"), null, EmploymentStatus.ACTIVE, null);

			service.createTeacher(request);

			verify(teacherRepository).saveAndFlush(any(Teacher.class));
		}

		@Test
		@DisplayName("document collision → ConflictException(TEACHER_DOCUMENT_TAKEN)")
		void documentTaken() {
			Teacher existing = teacher("X", "Y", "12345678", null, null);
			withId(existing, UUID.randomUUID());
			when(teacherRepository.findByDocument(eq(DocumentType.DNI), eq("12345678")))
					.thenReturn(Optional.of(existing));

			CreateTeacherRequest request = new CreateTeacherRequest(
					DocumentType.DNI, "12345678", "Ada", "Lovelace", null,
					null, null, null, null, null, null, null, null, null);

			assertThatThrownBy(() -> service.createTeacher(request))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("TEACHER_DOCUMENT_TAKEN"));

			verify(teacherRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("email collision → ConflictException(TEACHER_EMAIL_TAKEN)")
		void emailTaken() {
			when(teacherRepository.findByDocument(any(), any())).thenReturn(Optional.empty());
			Teacher existing = teacher("X", "Y", "99", "dup@acme.test", null);
			withId(existing, UUID.randomUUID());
			when(teacherRepository.findByEmailIgnoreCase(eq("dup@acme.test")))
					.thenReturn(Optional.of(existing));

			CreateTeacherRequest request = new CreateTeacherRequest(
					DocumentType.DNI, "12345678", "Ada", "L", null,
					null, null, "Dup@acme.test", null, null, null, null, null, null);

			assertThatThrownBy(() -> service.createTeacher(request))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("TEACHER_EMAIL_TAKEN"));

			verify(teacherRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("DataIntegrityViolation on save → ConflictException(TEACHER_DOCUMENT_TAKEN) (race)")
		void dbRace() {
			when(teacherRepository.findByDocument(any(), any())).thenReturn(Optional.empty());
			when(mapper.fromCreate(any())).thenReturn(teacher("a", "b", "1", null, null));
			when(teacherRepository.saveAndFlush(any(Teacher.class)))
					.thenThrow(new DataIntegrityViolationException("dup"));

			CreateTeacherRequest request = new CreateTeacherRequest(
					DocumentType.DNI, "12345678", "Ada", "L", null,
					null, null, null, null, null, null, null, null, null);

			assertThatThrownBy(() -> service.createTeacher(request))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("TEACHER_DOCUMENT_TAKEN"));
		}
	}

	// =========================================================================
	// updateTeacher
	// =========================================================================

	@Nested
	@DisplayName("updateTeacher")
	class UpdateTeacher {

		@Test
		@DisplayName("empty patch is a no-op (returns current snapshot)")
		void noOp() {
			Teacher t = teacher("A", "B", "1", "a@x.test", null);
			when(teacherRepository.findByPublicUuid(TEACHER_PUBLIC)).thenReturn(Optional.of(t));

			UpdateTeacherRequest empty = new UpdateTeacherRequest(
					null, null, null, null, null, null, null, null,
					null, null, null, null, null, null);

			service.updateTeacher(TEACHER_PUBLIC, empty);

			verify(mapper, never()).applyUpdate(any(), any());
			verify(teacherRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("changing email to one taken by another teacher → 409 TEACHER_EMAIL_TAKEN")
		void emailCollisionOnUpdate() {
			Teacher t = teacher("A", "B", "1", "old@x.test", null);
			Teacher other = teacher("C", "D", "2", "new@x.test", null);
			withId(other, UUID.randomUUID());
			withId(t, TEACHER_INTERNAL);

			when(teacherRepository.findByPublicUuid(TEACHER_PUBLIC)).thenReturn(Optional.of(t));
			when(teacherRepository.findByEmailIgnoreCase(eq("new@x.test")))
					.thenReturn(Optional.of(other));

			UpdateTeacherRequest patch = new UpdateTeacherRequest(
					null, null, null, null, null, null, null, "new@x.test",
					null, null, null, null, null, null);

			assertThatThrownBy(() -> service.updateTeacher(TEACHER_PUBLIC, patch))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("TEACHER_EMAIL_TAKEN"));
		}

		@Test
		@DisplayName("teacher not found → 404 RESOURCE_NOT_FOUND")
		void notFound() {
			when(teacherRepository.findByPublicUuid(TEACHER_PUBLIC)).thenReturn(Optional.empty());
			UpdateTeacherRequest patch = new UpdateTeacherRequest(
					null, null, "Ada", null, null, null, null, null,
					null, null, null, null, null, null);
			assertThatThrownBy(() -> service.updateTeacher(TEACHER_PUBLIC, patch))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// linkUser
	// =========================================================================

	@Nested
	@DisplayName("linkUser")
	class LinkUser {

		@Test
		@DisplayName("happy path — sets userId and saves")
		void happyPath() {
			Teacher t = teacher("A", "B", "1", "a@x.test", null);
			withId(t, TEACHER_INTERNAL);
			User u = makeUser(USER_INTERNAL, USER_PUBLIC, "a@x.test", Set.of(UserRole.TEACHER));

			when(teacherRepository.findByPublicUuid(TEACHER_PUBLIC)).thenReturn(Optional.of(t));
			when(userRepository.findByPublicUuid(USER_PUBLIC)).thenReturn(Optional.of(u));
			when(teacherRepository.findByUserId(USER_INTERNAL)).thenReturn(Optional.empty());
			when(teacherRepository.saveAndFlush(any(Teacher.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			service.linkUser(TEACHER_PUBLIC, new LinkTeacherUserRequest(USER_PUBLIC));

			ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
			verify(teacherRepository).saveAndFlush(captor.capture());
			assertThat(captor.getValue().getUserId()).isEqualTo(USER_INTERNAL);
		}

		@Test
		@DisplayName("teacher already has user → 409 TEACHER_ALREADY_HAS_USER")
		void alreadyHasUser() {
			Teacher t = teacher("A", "B", "1", null, null);
			t.setUserId(UUID.randomUUID());
			when(teacherRepository.findByPublicUuid(TEACHER_PUBLIC)).thenReturn(Optional.of(t));

			assertThatThrownBy(() ->
					service.linkUser(TEACHER_PUBLIC, new LinkTeacherUserRequest(USER_PUBLIC)))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("TEACHER_ALREADY_HAS_USER"));
		}

		@Test
		@DisplayName("user not found → 404")
		void userNotFound() {
			Teacher t = teacher("A", "B", "1", null, null);
			when(teacherRepository.findByPublicUuid(TEACHER_PUBLIC)).thenReturn(Optional.of(t));
			when(userRepository.findByPublicUuid(USER_PUBLIC)).thenReturn(Optional.empty());

			assertThatThrownBy(() ->
					service.linkUser(TEACHER_PUBLIC, new LinkTeacherUserRequest(USER_PUBLIC)))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("user lacks TEACHER role → 409 USER_NOT_TEACHER_ROLE")
		void userNotTeacherRole() {
			Teacher t = teacher("A", "B", "1", null, null);
			User u = makeUser(USER_INTERNAL, USER_PUBLIC, "a@x.test", Set.of(UserRole.STUDENT));
			when(teacherRepository.findByPublicUuid(TEACHER_PUBLIC)).thenReturn(Optional.of(t));
			when(userRepository.findByPublicUuid(USER_PUBLIC)).thenReturn(Optional.of(u));

			assertThatThrownBy(() ->
					service.linkUser(TEACHER_PUBLIC, new LinkTeacherUserRequest(USER_PUBLIC)))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("USER_NOT_TEACHER_ROLE"));
		}

		@Test
		@DisplayName("user already linked to another teacher → 409 USER_ALREADY_LINKED_TO_TEACHER")
		void doubleLink() {
			Teacher t = teacher("A", "B", "1", null, null);
			Teacher other = teacher("C", "D", "2", null, null);
			User u = makeUser(USER_INTERNAL, USER_PUBLIC, "a@x.test", Set.of(UserRole.TEACHER));

			when(teacherRepository.findByPublicUuid(TEACHER_PUBLIC)).thenReturn(Optional.of(t));
			when(userRepository.findByPublicUuid(USER_PUBLIC)).thenReturn(Optional.of(u));
			when(teacherRepository.findByUserId(USER_INTERNAL)).thenReturn(Optional.of(other));

			assertThatThrownBy(() ->
					service.linkUser(TEACHER_PUBLIC, new LinkTeacherUserRequest(USER_PUBLIC)))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("USER_ALREADY_LINKED_TO_TEACHER"));
		}
	}

	// =========================================================================
	// invite
	// =========================================================================

	@Nested
	@DisplayName("invite")
	class Invite {

		@Test
		@DisplayName("happy path — delegates to UserInvitationService with metadata.teacherId")
		void happyPath() {
			Teacher t = teacher("Ada", "Lovelace", "1", "ada@acme.test", null);
			withId(t, TEACHER_INTERNAL);
			when(teacherRepository.findByPublicUuid(TEACHER_PUBLIC)).thenReturn(Optional.of(t));

			InvitationResponse stub = new InvitationResponse(
					UUID.randomUUID(), "ada@acme.test", "Ada", "Lovelace",
					Set.of("TEACHER"), InvitationStatus.PENDING,
					"tok", Instant.now().plusSeconds(3600), null, null, Instant.now());
			when(invitationService.createInvitation(any(CreateInvitationRequest.class)))
					.thenReturn(stub);

			InviteTeacherResponse response = service.invite(TEACHER_PUBLIC);

			assertThat(response.invitationToken()).isEqualTo("tok");
			assertThat(response.teacherPublicUuid()).isEqualTo(TEACHER_PUBLIC);

			ArgumentCaptor<CreateInvitationRequest> captor =
					ArgumentCaptor.forClass(CreateInvitationRequest.class);
			verify(invitationService, times(1)).createInvitation(captor.capture());
			CreateInvitationRequest sent = captor.getValue();
			assertThat(sent.email()).isEqualTo("ada@acme.test");
			assertThat(sent.roles()).containsExactly("TEACHER");
			assertThat(sent.metadata())
					.containsEntry(TeacherServiceImpl.METADATA_TEACHER_ID_KEY,
							TEACHER_INTERNAL.toString());
		}

		@Test
		@DisplayName("teacher already has user → 409 TEACHER_ALREADY_HAS_USER")
		void alreadyHasUser() {
			Teacher t = teacher("A", "B", "1", "a@x.test", UUID.randomUUID());
			when(teacherRepository.findByPublicUuid(TEACHER_PUBLIC)).thenReturn(Optional.of(t));

			assertThatThrownBy(() -> service.invite(TEACHER_PUBLIC))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("TEACHER_ALREADY_HAS_USER"));

			verify(invitationService, never()).createInvitation(any());
		}

		@Test
		@DisplayName("teacher has no email → 422 TEACHER_NEEDS_EMAIL_TO_INVITE")
		void noEmail() {
			Teacher t = teacher("A", "B", "1", null, null);
			when(teacherRepository.findByPublicUuid(TEACHER_PUBLIC)).thenReturn(Optional.of(t));

			assertThatThrownBy(() -> service.invite(TEACHER_PUBLIC))
					.isInstanceOfSatisfying(BusinessException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("TEACHER_NEEDS_EMAIL_TO_INVITE"));
		}
	}

	// =========================================================================
	// deleteTeacher
	// =========================================================================

	@Test
	@DisplayName("deleteTeacher — soft deletes via repository.delete when no active assignments")
	void deleteHappyPath() {
		Teacher t = teacher("A", "B", "1", null, null);
		when(teacherRepository.findByPublicUuid(TEACHER_PUBLIC)).thenReturn(Optional.of(t));
		when(assignmentRepository.existsActiveByTeacher(t)).thenReturn(false);

		service.deleteTeacher(TEACHER_PUBLIC);

		verify(teacherRepository).delete(t);
	}

	@Test
	@DisplayName("deleteTeacher — refuses with 409 TEACHER_HAS_ACTIVE_ASSIGNMENTS when active rows exist")
	void deleteRefusedWhenActiveAssignmentsExist() {
		Teacher t = teacher("A", "B", "1", null, null);
		when(teacherRepository.findByPublicUuid(TEACHER_PUBLIC)).thenReturn(Optional.of(t));
		when(assignmentRepository.existsActiveByTeacher(t)).thenReturn(true);

		assertThatThrownBy(() -> service.deleteTeacher(TEACHER_PUBLIC))
				.isInstanceOfSatisfying(ConflictException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("TEACHER_HAS_ACTIVE_ASSIGNMENTS"));

		verify(teacherRepository, never()).delete(any());
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private static Teacher teacher(String first, String last, String doc, String email, UUID userId) {
		Teacher t = new Teacher();
		t.setPublicUuid(TEACHER_PUBLIC);
		t.setDocumentType(DocumentType.DNI);
		t.setDocumentNumber(doc);
		t.setFirstName(first);
		t.setLastName(last);
		t.setEmail(email);
		t.setUserId(userId);
		t.setEmploymentStatus(EmploymentStatus.ACTIVE);
		return t;
	}

	private static User makeUser(UUID internalId, UUID publicId, String email, Set<UserRole> roles) {
		User u = new User();
		u.setEmail(email);
		u.setPublicUuid(publicId);
		u.setRoleSet(roles);
		setField(u, "id", internalId);
		return u;
	}

	private static void withId(Object entity, UUID id) {
		setField(entity, "id", id);
	}

	private static void setField(Object target, String name, Object value) {
		Class<?> cls = target.getClass();
		while (cls != null) {
			try {
				Field f = cls.getDeclaredField(name);
				f.setAccessible(true);
				f.set(target, value);
				return;
			}
			catch (NoSuchFieldException ignored) {
				cls = cls.getSuperclass();
			}
			catch (IllegalAccessException ex) {
				throw new RuntimeException(ex);
			}
		}
		throw new RuntimeException("Field not found: " + name);
	}
}
