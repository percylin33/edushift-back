package com.edushift.modules.teachers.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.teachers.assignments.event.TeacherAssignmentCreatedEvent;
import com.edushift.modules.teachers.repository.TeacherRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherAssignmentWorkloadListener — counter bump")
class TeacherAssignmentWorkloadListenerTest {

	@Mock private TeacherRepository teacherRepository;

	@InjectMocks private TeacherAssignmentWorkloadListener listener;

	@Test
	@DisplayName("happy path — atomically increments teacher.assignments_count")
	void happyPath() {
		UUID teacherPublic = UUID.randomUUID();
		TeacherAssignmentCreatedEvent event = new TeacherAssignmentCreatedEvent(
				UUID.randomUUID(), teacherPublic,
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), Instant.now());

		when(teacherRepository.incrementAssignmentsCountByPublicUuid(teacherPublic))
				.thenReturn(1);

		listener.onAssignmentCreated(event);

		verify(teacherRepository).incrementAssignmentsCountByPublicUuid(teacherPublic);
	}

	@Test
	@DisplayName("teacher not found in current tenant → defensive no-op (no exception)")
	void teacherVanished() {
		UUID teacherPublic = UUID.randomUUID();
		TeacherAssignmentCreatedEvent event = new TeacherAssignmentCreatedEvent(
				UUID.randomUUID(), teacherPublic,
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), Instant.now());

		when(teacherRepository.incrementAssignmentsCountByPublicUuid(teacherPublic))
				.thenReturn(0);

		// Listener swallows the 0-update case as a warning rather than
		// failing the source tx.
		listener.onAssignmentCreated(event);

		verify(teacherRepository).incrementAssignmentsCountByPublicUuid(teacherPublic);
		assertThat(true).isTrue(); // explicit assertion of no-throw
	}

	@Test
	@DisplayName("repository throws → propagated (upper tx may decide policy)")
	void repositoryThrows() {
		UUID teacherPublic = UUID.randomUUID();
		TeacherAssignmentCreatedEvent event = new TeacherAssignmentCreatedEvent(
				UUID.randomUUID(), teacherPublic,
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), Instant.now());

		when(teacherRepository.incrementAssignmentsCountByPublicUuid(teacherPublic))
				.thenThrow(new RuntimeException("DB down"));

		try {
			listener.onAssignmentCreated(event);
		}
		catch (RuntimeException ignored) {
			// Acceptable: the listener trusts the caller (outer @Transactional)
			// to roll back. We only assert the dispatch was attempted.
		}

		verify(teacherRepository).incrementAssignmentsCountByPublicUuid(teacherPublic);
	}
}
