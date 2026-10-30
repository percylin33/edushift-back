package com.edushift.modules.attendance.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.notifications.event.NotificationEvent;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class SessionClosedAbsencesListenerTest {

	@Mock StudentGuardianRepository studentGuardianRepository;
	@Mock UserRepository userRepository;
	@Mock ApplicationEventPublisher eventPublisher;
	@Mock PlatformTransactionManager transactionManager;
	@InjectMocks SessionClosedAbsencesListener listener;

	@BeforeEach
	void setTenant() {
		TenantContext.set(UUID.randomUUID());
	}

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	@Test
	void fanOut_publishesOneNotificationPerLinkedGuardian() {
		UUID tenantId = TenantContext.currentRequired();
		UUID studentId = UUID.randomUUID();
		UUID studentPublic = UUID.randomUUID();
		UUID recordPublic = UUID.randomUUID();
		UUID guardianUserInternal = UUID.randomUUID();
		UUID guardianUserPublic = UUID.fromString("00000000-0000-0000-0000-000000000201");
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

		Student student = new Student();
		setField(student, "id", studentId);
		student.setPublicUuid(studentPublic);
		student.setFirstName("Ana");
		student.setLastName("Perez");

		Guardian guardian = new Guardian();
		setField(guardian, "id", UUID.randomUUID());
		guardian.setUserId(guardianUserPublic);
		guardian.setEmail("madre@test.com");
		guardian.setFirstName("Madre");
		guardian.setLastName("Perez");

		StudentGuardian sg = new StudentGuardian();
		sg.setStudent(student);
		sg.setGuardian(guardian);

		when(studentGuardianRepository.findActiveByStudentIdsWithLinkedGuardian(anyList()))
				.thenReturn(List.of(sg));
		User u = new User();
		setField(u, "id", guardianUserInternal);
		u.setPublicUuid(guardianUserPublic);
		when(userRepository.findAllByPublicUuidIn(anySet())).thenReturn(List.of(u));

		SessionClosedAbsencesEvent event = new SessionClosedAbsencesEvent(
				tenantId,
				UUID.randomUUID(),
				"3A",
				Instant.parse("2026-09-19T12:00:00Z"),
				List.of(new SessionClosedAbsencesEvent.AbsentStudent(
						studentId, studentPublic, "Ana Perez", recordPublic)));

		listener.onSessionClosedAbsences(event);

		ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
		verify(eventPublisher, times(1)).publishEvent(captor.capture());
		NotificationEvent ne = captor.getValue();
		assertThat(ne.templateKey()).isEqualTo("STUDENT_ABSENT");
		assertThat(ne.recipients()).hasSize(1);
		assertThat(ne.recipients().get(0).userId()).isEqualTo(guardianUserPublic);
		verify(transactionManager).commit(any());
	}

	@Test
	void emptyAbsents_noop() {
		listener.onSessionClosedAbsences(new SessionClosedAbsencesEvent(
				UUID.randomUUID(), UUID.randomUUID(), "A", Instant.now(), List.of()));
		verify(eventPublisher, never()).publishEvent(any());
	}

	private static void setField(Object target, String name, Object value) {
		try {
			Field f = target.getClass().getSuperclass() != null
					? findField(target.getClass(), name)
					: target.getClass().getDeclaredField(name);
			f.setAccessible(true);
			f.set(target, value);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
		Class<?> c = type;
		while (c != null) {
			try {
				return c.getDeclaredField(name);
			}
			catch (NoSuchFieldException ignored) {
				c = c.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name);
	}
}
