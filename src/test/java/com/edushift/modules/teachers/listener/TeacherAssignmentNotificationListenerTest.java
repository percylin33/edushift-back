package com.edushift.modules.teachers.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.teachers.assignments.event.TeacherAssignmentCreatedEvent;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherAssignmentNotificationListener — fan-out to teacher + students")
class TeacherAssignmentNotificationListenerTest {

	@Mock private TeacherRepository teacherRepository;
	@Mock private SectionRepository sectionRepository;
	@Mock private StudentEnrollmentRepository studentEnrollmentRepository;
	@Mock private UserRepository userRepository;
	@Mock private ApplicationEventPublisher eventPublisher;

	@InjectMocks private TeacherAssignmentNotificationListener listener;

	@Test
	@DisplayName("happy path: 1 teacher + 2 enrolled students (with user) → 2 NotificationEvents")
	void happyPath() {
		Teacher teacher = makeTeacher("Ada", "Lovelace", "ada@school.edu",
				UUID.randomUUID());
		Section section = makeSection("A");
		UUID teacherPublicUserId = UUID.randomUUID();

		when(teacherRepository.findByPublicUuid(any(UUID.class)))
				.thenReturn(Optional.of(teacher));
		when(sectionRepository.findByPublicUuid(any(UUID.class)))
				.thenReturn(Optional.of(section));
		// DEBT-NOTIF-4 fix: the publisher now resolves teacher.userId (internal)
		// -> publicUuid via userRepository. Stub the lookup.
		when(userRepository.findById(teacher.getUserId()))
				.thenReturn(Optional.of(makeUserWithPublicUuid(teacher.getUserId(), teacherPublicUserId)));

		Student s1 = makeStudent("11111111", "11111111@school.edu", UUID.randomUUID());
		Student s2 = makeStudent("22222222", "22222222@school.edu", UUID.randomUUID());
		Student s3 = makeStudent("33333333", null, null); // skip — no userId
		UUID s1PublicUserId = UUID.randomUUID();
		UUID s2PublicUserId = UUID.randomUUID();

		when(studentEnrollmentRepository.findActiveBySection(section))
				.thenReturn(List.of(
						makeEnrollment(s1, section),
						makeEnrollment(s2, section),
						makeEnrollment(s3, section)));
		// Bulk lookup for the two students with userIds (s1 + s2); s3 has no userId.
		java.util.List<User> bulkUsers = new java.util.ArrayList<>();
		bulkUsers.add(makeUserWithPublicUuid(s1.getUserId(), s1PublicUserId));
		bulkUsers.add(makeUserWithPublicUuid(s2.getUserId(), s2PublicUserId));
		java.util.Map<UUID, User> bulkByInternalId = new java.util.HashMap<>();
		for (User u : bulkUsers) bulkByInternalId.put(u.getId(), u);
		when(userRepository.findAllById(any(java.lang.Iterable.class)))
				.thenAnswer(invocation -> {
					@SuppressWarnings("unchecked")
					java.util.Collection<UUID> ids =
							(java.util.Collection<UUID>) invocation.getArgument(0);
					java.util.List<User> out = new java.util.ArrayList<>();
					for (UUID id : ids) {
						User u = bulkByInternalId.get(id);
						if (u != null) out.add(u);
					}
					return out;
				});

		TeacherAssignmentCreatedEvent event = newEvent();

		listener.onAssignmentCreated(event);

		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher, times(2)).publishEvent(captor.capture());
		List<Object> published = captor.getAllValues();

		assertThat(published).hasSize(2);

		com.edushift.modules.notifications.event.NotificationEvent first =
				(com.edushift.modules.notifications.event.NotificationEvent) published.get(0);
		assertThat(first.templateKey()).isEqualTo("TEACHER_ASSIGNED");
		assertThat(first.recipients()).hasSize(1);
		// The recipient must be the publicUuid, NOT the internal id.
		assertThat(first.recipients().get(0).userId()).isEqualTo(teacherPublicUserId);

		com.edushift.modules.notifications.event.NotificationEvent second =
				(com.edushift.modules.notifications.event.NotificationEvent) published.get(1);
		assertThat(second.templateKey()).isEqualTo("SECTION_NEW_TEACHER");
		assertThat(second.recipients()).hasSize(2);
		assertThat(second.recipients())
				.extracting(com.edushift.modules.notifications.event.NotificationEvent.Recipient::userId)
				.containsExactlyInAnyOrder(s1PublicUserId, s2PublicUserId);
	}

	@Test
	@DisplayName("teacher without linked user → skip TEACHER_ASSIGNED, still fan-out to students")
	void teacherWithoutUser() {
		Teacher teacher = makeTeacher("Ada", "Lovelace", "ada@school.edu", null);
		Section section = makeSection("A");
		Student s1 = makeStudent("11111111", "11111111@school.edu", UUID.randomUUID());
		UUID s1PublicUserId = UUID.randomUUID();

		when(teacherRepository.findByPublicUuid(any(UUID.class)))
				.thenReturn(Optional.of(teacher));
		when(sectionRepository.findByPublicUuid(any(UUID.class)))
				.thenReturn(Optional.of(section));
		when(studentEnrollmentRepository.findActiveBySection(section))
				.thenReturn(List.of(makeEnrollment(s1, section)));
		when(userRepository.findAllById(any(java.lang.Iterable.class)))
				.thenAnswer(invocation -> {
					@SuppressWarnings("unchecked")
					java.util.Collection<UUID> ids =
							(java.util.Collection<UUID>) invocation.getArgument(0);
					java.util.List<User> out = new java.util.ArrayList<>();
					if (ids.contains(s1.getUserId())) {
						out.add(makeUserWithPublicUuid(s1.getUserId(), s1PublicUserId));
					}
					return out;
				});

		listener.onAssignmentCreated(newEvent());

		verify(eventPublisher, times(1)).publishEvent(any(
				com.edushift.modules.notifications.event.NotificationEvent.class));
	}

	@Test
	@DisplayName("section with 0 active enrollments → only TEACHER_ASSIGNED fires")
	void sectionEmpty() {
		Teacher teacher = makeTeacher("Ada", "Lovelace", "ada@school.edu", UUID.randomUUID());
		Section section = makeSection("A");
		UUID teacherPublicUserId = UUID.randomUUID();

		when(teacherRepository.findByPublicUuid(any(UUID.class)))
				.thenReturn(Optional.of(teacher));
		when(sectionRepository.findByPublicUuid(any(UUID.class)))
				.thenReturn(Optional.of(section));
		when(studentEnrollmentRepository.findActiveBySection(section))
				.thenReturn(List.of());
		when(userRepository.findById(teacher.getUserId()))
				.thenReturn(Optional.of(makeUserWithPublicUuid(teacher.getUserId(), teacherPublicUserId)));

		listener.onAssignmentCreated(newEvent());

		verify(eventPublisher, times(1)).publishEvent(any(
				com.edushift.modules.notifications.event.NotificationEvent.class));
	}

	// -----------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------

	private static TeacherAssignmentCreatedEvent newEvent() {
		return new TeacherAssignmentCreatedEvent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				Instant.now());
	}

	private static Teacher makeTeacher(String first, String last, String email, UUID userId) {
		Teacher t = new Teacher();
		t.setFirstName(first);
		t.setLastName(last);
		t.setEmail(email);
		t.setEmploymentStatus(EmploymentStatus.ACTIVE);
		t.setUserId(userId);
		t.setPublicUuid(UUID.randomUUID());
		setField(t, "id", UUID.randomUUID());
		return t;
	}

	private static Section makeSection(String name) {
		AcademicLevel level = new AcademicLevel();
		level.setCode("PRIMARIA");
		level.setName("Primaria");
		level.setOrdinal(1);

		Grade g = new Grade();
		g.setName("2do Primaria");
		g.setOrdinal(1);
		g.setLevel(level);

		Section s = new Section();
		s.setName(name);
		s.setGrade(g);
		s.setDisplayOrder(1);
		s.setPublicUuid(UUID.randomUUID());
		setField(s, "id", UUID.randomUUID());
		return s;
	}

	private static Student makeStudent(String docNumber, String email, UUID userId) {
		Student s = new Student();
		s.setDocumentType(DocumentType.DNI);
		s.setDocumentNumber(docNumber);
		s.setFirstName("Alumno" + docNumber);
		s.setLastName("Apellido");
		s.setGender(Gender.NOT_SPECIFIED);
		s.setBirthDate(LocalDate.of(2015, 1, 1));
		s.setEmail(email);
		s.setUserId(userId);
		s.setPublicUuid(UUID.randomUUID());
		setField(s, "id", UUID.randomUUID());
		return s;
	}

	private static StudentEnrollment makeEnrollment(Student s, Section section) {
		StudentEnrollment e = new StudentEnrollment();
		e.setStudent(s);
		e.setSection(section);
		e.setStatus(StudentEnrollmentStatus.ACTIVE);
		setField(e, "id", UUID.randomUUID());
		e.setPublicUuid(UUID.randomUUID());
		return e;
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

	/**
	 * DEBT-NOTIF-4 helper: builds a {@link User} with the given internal
	 * id and the given publicUuid. Used to stub the
	 * {@code userRepository.findById} / {@code findAllById} lookups that
	 * the publisher now performs to resolve internal-id recipients
	 * to the publicUuid that the FK expects.
	 */
	private static User makeUserWithPublicUuid(UUID internalId, UUID publicUuid) {
		User u = new User();
		setField(u, "id", internalId);
		u.setPublicUuid(publicUuid);
		u.setEmail("stub+" + internalId + "@school.edu");
		return u;
	}
}
