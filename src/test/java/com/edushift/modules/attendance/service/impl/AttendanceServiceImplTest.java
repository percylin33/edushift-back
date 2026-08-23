package com.edushift.modules.attendance.service.impl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.edushift.modules.attendance.dto.CreateSessionRequest;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import com.edushift.modules.attendance.exception.SessionAlreadyOpenException;
import com.edushift.modules.attendance.exception.SessionClosedException;
import com.edushift.modules.attendance.mapper.AttendanceMapper;
import com.edushift.modules.attendance.repository.AttendanceRecordRepository;
import com.edushift.modules.attendance.repository.AttendanceSessionRepository;
import com.edushift.modules.attendance.repository.StudentAttendanceQrRepository;
import com.edushift.modules.attendance.service.AttendanceUserCache;
import com.edushift.modules.attendance.service.QrTokenService;
import com.edushift.modules.notifications.event.NotificationEvent;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.service.TenantSettingsService;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {
	@Mock AttendanceSessionRepository sessionRepo;
	@Mock AttendanceRecordRepository recordRepo;
	@Mock StudentAttendanceQrRepository qrRepo;
	@Mock AttendanceMapper mapper;
	@Mock AttendanceUserCache userCache;
	@Mock QrTokenService qrTokenService;
	@Mock CurrentUserProvider currentUser;
	@Mock com.edushift.modules.academic.section.repository.SectionRepository sectionRepo;
	@Mock com.edushift.modules.students.repository.StudentRepository studentRepo;
	@Mock StudentEnrollmentRepository enrollmentRepo;
	@Mock com.edushift.modules.attendance.audit.AttendanceAuditLogger auditLogger;
	@Mock TenantSettingsService tenantSettings;
	@Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
	@Mock com.edushift.modules.attendance.events.AttendanceEventPublisher realtimePublisher;
	@Mock StudentGuardianRepository studentGuardianRepo; // Sprint 9A / BE-9A.1
	@Mock UserRepository userRepository; // DEBT-NOTIF-4 (Sprint 9A)
	@InjectMocks AttendanceServiceImpl service;

	@BeforeEach
	void setUp() {
		// Sprint 9A / BE-9A.1: TenantContext.currentRequired() is called
		// from the notification publisher. Set it to the same id we mock
		// currentUser.currentTenantId() with so the publisher doesn't
		// throw TenantContextRequiredException.
		UUID tenantId = UUID.randomUUID();
		TenantContext.set(tenantId);
		lenient().when(currentUser.currentTenantId()).thenReturn(Optional.of(tenantId));
		lenient().when(currentUser.currentUserId()).thenReturn(Optional.of(UUID.randomUUID()));
	}

	@org.junit.jupiter.api.AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	// =====================================================================
	// Existing idempotency contract tests (unchanged from previous spec)
	// =====================================================================

	@Test void openSessionIsIdempotentWhenAlreadyActive() {
		var s = new AttendanceSession();
		s.setStatus(AttendanceSessionStatus.ACTIVE);
		s.setPublicUuid(UUID.randomUUID());
		when(sectionRepo.findByPublicUuid(any())).thenReturn(Optional.of(new com.edushift.modules.academic.section.entity.Section()));
		when(sessionRepo.findActiveBySectionDaySlot(any(), any(), any())).thenReturn(Optional.of(s));
		service.openSession(new CreateSessionRequest(UUID.randomUUID(), LocalDate.now(), AttendanceSessionSlot.MORNING, Instant.now(), null));
	}

	@Test void closeSessionIsIdempotentWhenAlreadyClosed() {
		var s = new AttendanceSession();
		s.setStatus(AttendanceSessionStatus.CLOSED);
		s.setPublicUuid(UUID.randomUUID());
		when(sessionRepo.findByPublicUuid(any())).thenReturn(Optional.of(s));
		service.closeSession(UUID.randomUUID());
	}

	// =====================================================================
	// Sprint 9A / BE-9A.1 — STUDENT_ABSENT notification fan-out
	// =====================================================================
	// The original BE-9.3 publisher sent notifications to
	// student.userId (almost always null for K-12 students). Sprint
	// 9A fixes that by resolving the student's primary guardian via
	// StudentGuardianRepository and dispatching to guardian.userId.
	// =====================================================================

	@Nested
	@org.junit.jupiter.api.DisplayName("closeSession -- STUDENT_ABSENT notification fan-out (Sprint 9A / BE-9A.1)")
	class AbsenceFanOut {

		@Test
		@org.junit.jupiter.api.DisplayName(
				"SPRINT9A-CASCADE: happy path -- 3 ausentes con guardian con userId => 3 NotificationEvent al parent (no al student)")
		void happyPath_threeStudentsWithGuardian_eachGetsOneEvent() {
			AttendanceSession session = activeSession();
			studentRepo_whenFindAll();
			givenExistingRecordsForSession(session, List.of(/* no checked-in */));
			enrollmentRepo_willReturnNEnrollments(session, 3);

			// 3 students, 3 guardians (one per student), each with userId set.
			List<StudentGuardian> sgs = new ArrayList<>();
			sgs.add(studentGuardianWithLinkedUser(student(1L), guardian(101L, "madre1@test.com")));
			sgs.add(studentGuardianWithLinkedUser(student(2L), guardian(102L, "padre2@test.com")));
			sgs.add(studentGuardianWithLinkedUser(student(3L), guardian(103L, null /* email optional */)));
			when(studentGuardianRepo.findActiveByStudentIdsWithLinkedGuardian(anyList()))
					.thenReturn(sgs);
			// DEBT-NOTIF-4: stub the userRepository bulk lookup to return
			// the 3 guardians with their publicUuid derived from the
			// internalId. We use a Map<UUID internalId, UUID publicUuid>
			// to keep the mapping deterministic and match the expected
			// publicUuids below (...201, ...202, ...203).
			java.util.Map<UUID, UUID> internalToPublic = new java.util.HashMap<>();
			List<UUID> allStudentIds = new java.util.ArrayList<>();
			for (int i = 0; i < sgs.size(); i++) {
				UUID guardianUserInternal = sgs.get(i).getGuardian().getUserId();
				UUID studentInternal = sgs.get(i).getStudent().getId();
				allStudentIds.add(studentInternal);
				// PublicUuid matches the test's hardcoded expectations
				// (ordinal+200L: 201, 202, 203).
				internalToPublic.put(guardianUserInternal, UUID.fromString(
						"00000000-0000-0000-0000-" + String.format("%012d", 201L + i)));
			}
			when(userRepository.findAllById(any(java.lang.Iterable.class)))
					.thenAnswer(invocation -> {
						@SuppressWarnings("unchecked")
						java.util.Collection<UUID> ids =
								(java.util.Collection<UUID>) invocation.getArgument(0);
						java.util.List<com.edushift.modules.auth.entity.User> out = new java.util.ArrayList<>();
						for (UUID id : ids) {
							UUID pub = internalToPublic.get(id);
							if (pub != null) {
								out.add(makeGuardianUser(id, pub));
							}
						}
						return out;
					});

			service.closeSession(session.getPublicUuid());

			ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
			verify(eventPublisher, times(3)).publishEvent(captor.capture());

			List<NotificationEvent> events = captor.getAllValues();
			assertThat(events).hasSize(3);
			for (NotificationEvent e : events) {
				assertThat(e.templateKey()).isEqualTo("STUDENT_ABSENT");
				assertThat(e.category().name()).isEqualTo("ABSENCE");
				assertThat(e.tenantId()).isNotNull();
				assertThat(e.recipients()).hasSize(1);
                assertThat(e.payload()).containsKey("studentPublicUuid");
                assertThat(e.sourceId()).isNotNull();
			}
			// The CRITICAL assertion: recipients are guardian userIds,
			// NOT student userIds. The original bug was sending to
			// student.userId, which is null for K-12 students.
			List<UUID> recipientIds = events.stream()
					.flatMap(e -> e.recipients().stream())
					.map(NotificationEvent.Recipient::userId)
					.toList();
			// helper uses ordinal+100L for the userId UUID, so
			// guardian(101L, ...) → userId = "...201", etc.
			assertThat(recipientIds).containsExactlyInAnyOrder(
					UUID.fromString("00000000-0000-0000-0000-000000000201"),
					UUID.fromString("00000000-0000-0000-0000-000000000202"),
					UUID.fromString("00000000-0000-0000-0000-000000000203"));
			// And NEVER any student userId.
			assertThat(recipientIds).doesNotContain(
					UUID.fromString("00000000-0000-0000-0000-000000000001"));
		}

		@Test
		@org.junit.jupiter.api.DisplayName(
				"SPRINT9A-CASCADE: student sin guardian linkeado => 0 events (no NPE)")
		void noGuardianNoCrash_noEventsPublished() {
			AttendanceSession session = activeSession();
			studentRepo_whenFindAll();
			givenExistingRecordsForSession(session, List.of());
			enrollmentRepo_willReturnNEnrollments(session, 2);

			// No guardians at all -- the bulk lookup returns empty.
			when(studentGuardianRepo.findActiveByStudentIdsWithLinkedGuardian(anyList()))
					.thenReturn(List.of());

			service.closeSession(session.getPublicUuid());

			verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));
		}

		@Test
		@org.junit.jupiter.api.DisplayName(
				"SPRINT9A-CASCADE: guardian con userId=null => skipped (no event)")
		void guardianWithoutUserId_skipped() {
			AttendanceSession session = activeSession();
			studentRepo_whenFindAll();
			givenExistingRecordsForSession(session, List.of());
			enrollmentRepo_willReturnNEnrollments(session, 1);

Student s = student(1L);
			// Guardian has NO userId link -- the bulk query would have
			// filtered it (g.userId is not null), but we model the
			// pathological case where someone bypasses the filter
			// (e.g. inserts directly in DB).
			Guardian g = new Guardian();
			setField(g, "id", UUID.randomUUID());
			g.setPublicUuid(UUID.randomUUID());
			g.setUserId(null);
			g.setEmail("orphan@test.com");
			g.setFirstName("Orphan");
			g.setLastName("Guardian");
			StudentGuardian sg = studentGuardianWith(s, g);
			when(studentGuardianRepo.findActiveByStudentIdsWithLinkedGuardian(anyList()))
					.thenReturn(List.of(sg));

			service.closeSession(session.getPublicUuid());

			verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));
		}

		@Test
		@org.junit.jupiter.api.DisplayName(
				"SPRINT9A-CASCADE: bulk lookup uses ALL materialized student ids (no N+1)")
		void bulkLookupUsesAllStudentIds() {
			AttendanceSession session = activeSession();
			studentRepo_whenFindAll();
			givenExistingRecordsForSession(session, List.of());
			enrollmentRepo_willReturnNEnrollments(session, 5);

			when(studentGuardianRepo.findActiveByStudentIdsWithLinkedGuardian(anyList()))
					.thenReturn(List.of());

			service.closeSession(session.getPublicUuid());

			@SuppressWarnings("unchecked")
			ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
			verify(studentGuardianRepo).findActiveByStudentIdsWithLinkedGuardian(captor.capture());
			// The 5 student ids from the enrollment must be passed in a
			// SINGLE call -- not 5 separate calls.
			assertThat(captor.getValue()).hasSize(5);
		}
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	private AttendanceSession activeSession() {
		AttendanceSession s = new AttendanceSession();
		s.setStatus(AttendanceSessionStatus.ACTIVE);
		s.setPublicUuid(UUID.randomUUID());
		setField(s, "id", UUID.randomUUID());
		com.edushift.modules.academic.section.entity.Section sec = new com.edushift.modules.academic.section.entity.Section();
		sec.setName("A");
		sec.setPublicUuid(UUID.randomUUID());
		setField(sec, "id", UUID.randomUUID());
		s.setSection(sec);
		// Make sessionRepo.findByPublicUuid resolve this session so
		// closeSession's loadSession step passes.
		when(sessionRepo.findByPublicUuid(s.getPublicUuid())).thenReturn(Optional.of(s));
		// Make saveAndFlush round-trip the session (Mockito defaults to
		// null otherwise, which trips closeSession's NPE on
		// session.getPublicUuid() after the save call).
		when(sessionRepo.saveAndFlush(s)).thenReturn(s);
		return s;
	}

	private void studentRepo_whenFindAll() {
		// Default behaviour: nothing to mock — only called if any
		// student needs individual lookup. Sprint 9A fan-out uses the
		// bulk guardian query instead.
	}

	private void givenExistingRecordsForSession(AttendanceSession session, List<com.edushift.modules.attendance.entity.AttendanceRecord> records) {
		// Sprint 9A only invokes findBySessionOrderedByStudentName to
		// compute the diff between materialized and existing. With the
		// default empty list, every enrolled student is materialized.
		when(recordRepo.findBySessionOrderedByStudentName(session))
				.thenReturn(records);
	}

	@SuppressWarnings("unchecked")
	private void enrollmentRepo_willReturnNEnrollments(AttendanceSession session, int n) {
		List<StudentEnrollment> out = new ArrayList<>();
		for (int i = 1; i <= n; i++) {
			Student s = student((long) i);
			StudentEnrollment e = new StudentEnrollment();
			e.setStudent(s);
			e.setSection(session.getSection());
			out.add(e);
		}
		when(enrollmentRepo.findActiveBySection(session.getSection())).thenReturn(out);
	}

	private Student student(long internalId) {
		Student s = new Student();
		setField(s, "id", UUID.randomUUID());
		s.setPublicUuid(UUID.randomUUID());
		s.setFirstName("Student" + internalId);
		s.setLastName("Test");
		// CRUCIAL: student.userId is null (the original bug was using
		// this). Make it explicit so the test fails loudly if anyone
		// ever sets it (regression of the bug).
		s.setUserId(null);
		return s;
	}

	private Guardian guardian(long ordinal, String email) {
		Guardian g = new Guardian();
		setField(g, "id", UUID.randomUUID());
		// Stable derived UUIDs so the assertion can match by ordinal
		// (101, 102, 103) without depending on random UUIDs.
		g.setPublicUuid(UUID.fromString(
				"00000000-0000-0000-0000-" + String.format("%012d", ordinal)));
		g.setFirstName("G" + ordinal);
		g.setLastName("F" + ordinal);
		g.setEmail(email);
		g.setUserId(UUID.fromString(
				"00000000-0000-0000-0000-" + String.format("%012d", ordinal + 100L)));
		return g;
	}

	/**
	 * DEBT-NOTIF-4 helper: builds a {@link com.edushift.modules.auth.entity.User}
	 * with the given internal id and publicUuid. Used to stub the
	 * {@code userRepository.findAllById} bulk lookup in tests.
	 */
	private static com.edushift.modules.auth.entity.User makeGuardianUser(
			UUID internalId, UUID publicUuid) {
		com.edushift.modules.auth.entity.User u =
				new com.edushift.modules.auth.entity.User();
		setField(u, "id", internalId);
		u.setPublicUuid(publicUuid);
		u.setEmail("stub+" + internalId + "@test.com");
		return u;
	}

	private StudentGuardian studentGuardianWithLinkedUser(Student student, Guardian guardian) {
		StudentGuardian sg = new StudentGuardian();
		setField(sg, "id", UUID.randomUUID());
		sg.setStudent(student);
		sg.setGuardian(guardian);
		sg.setPrimaryContact(true);
		return sg;
	}

	private StudentGuardian studentGuardianWith(Student student, Guardian guardian) {
		StudentGuardian sg = new StudentGuardian();
		setField(sg, "id", UUID.randomUUID());
		sg.setStudent(student);
		sg.setGuardian(guardian);
		sg.setPrimaryContact(false);
		return sg;
	}

	private static void setField(Object target, String name, Object value) {
		try {
			Field f = findField(target.getClass(), name);
			f.setAccessible(true);
			f.set(target, value);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
		Class<?> c = cls;
		while (c != null) {
			try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) { c = c.getSuperclass(); }
		}
		throw new NoSuchFieldException(name);
	}
}