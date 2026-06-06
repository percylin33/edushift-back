package com.edushift.modules.teachers.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.modules.teachers.service.impl.TeacherServiceImpl;
import com.edushift.modules.users.events.InvitationAcceptedEvent;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeacherInvitationListenerTest {

	@Mock private TeacherRepository teacherRepository;

	@InjectMocks private TeacherInvitationListener listener;

	@Test
	@DisplayName("metadata.teacherId present and teacher exists → links userId")
	void linksTeacher() {
		UUID teacherInternalId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Teacher t = makeTeacher(teacherInternalId);

		when(teacherRepository.findById(teacherInternalId)).thenReturn(Optional.of(t));

		Map<String, Object> metadata = new HashMap<>();
		metadata.put(TeacherServiceImpl.METADATA_TEACHER_ID_KEY, teacherInternalId.toString());

		listener.onInvitationAccepted(new InvitationAcceptedEvent(
				UUID.randomUUID(), UUID.randomUUID(),
				userId, UUID.randomUUID(),
				UUID.randomUUID(), metadata, Instant.now()));

		ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
		verify(teacherRepository, times(1)).save(captor.capture());
		assertThat(captor.getValue().getUserId()).isEqualTo(userId);
	}

	@Test
	@DisplayName("metadata without teacherId → no-op")
	void noTeacherIdInMetadata() {
		listener.onInvitationAccepted(new InvitationAcceptedEvent(
				UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), Map.of("foo", "bar"), Instant.now()));

		verify(teacherRepository, never()).save(any());
		verify(teacherRepository, never()).findById(any());
	}

	@Test
	@DisplayName("teacher already linked to another user → no-op (no overwrite)")
	void doesNotOverwrite() {
		UUID teacherInternalId = UUID.randomUUID();
		UUID existingUserId = UUID.randomUUID();
		Teacher t = makeTeacher(teacherInternalId);
		t.setUserId(existingUserId);

		when(teacherRepository.findById(teacherInternalId)).thenReturn(Optional.of(t));

		Map<String, Object> metadata = Map.of(
				TeacherServiceImpl.METADATA_TEACHER_ID_KEY, teacherInternalId.toString());

		listener.onInvitationAccepted(new InvitationAcceptedEvent(
				UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), metadata, Instant.now()));

		verify(teacherRepository, never()).save(any());
		assertThat(t.getUserId()).isEqualTo(existingUserId);
	}

	@Test
	@DisplayName("teacher not found → no-op (warn-logged)")
	void teacherMissing() {
		UUID teacherInternalId = UUID.randomUUID();
		when(teacherRepository.findById(teacherInternalId)).thenReturn(Optional.empty());

		Map<String, Object> metadata = Map.of(
				TeacherServiceImpl.METADATA_TEACHER_ID_KEY, teacherInternalId.toString());

		listener.onInvitationAccepted(new InvitationAcceptedEvent(
				UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), metadata, Instant.now()));

		verify(teacherRepository, never()).save(any());
	}

	@Test
	@DisplayName("metadata.teacherId is malformed → no-op")
	void malformedTeacherId() {
		Map<String, Object> metadata = Map.of(
				TeacherServiceImpl.METADATA_TEACHER_ID_KEY, "not-a-uuid");

		listener.onInvitationAccepted(new InvitationAcceptedEvent(
				UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), metadata, Instant.now()));

		verify(teacherRepository, never()).findById(any());
		verify(teacherRepository, never()).save(any());
	}

	private static Teacher makeTeacher(UUID internalId) {
		Teacher t = new Teacher();
		t.setDocumentType(DocumentType.DNI);
		t.setDocumentNumber("12345678");
		t.setFirstName("Ada");
		t.setLastName("Lovelace");
		setField(t, "id", internalId);
		t.setPublicUuid(UUID.randomUUID());
		return t;
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
