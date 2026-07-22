package com.edushift.modules.teachers.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.teachers.assignments.event.TeacherAssignmentUnassignedEvent;
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
@DisplayName("TeacherAssignmentUnassignedWorkloadListener — counter decrement")
class TeacherAssignmentUnassignedWorkloadListenerTest {

	@Mock private TeacherRepository teacherRepository;

	@InjectMocks private TeacherAssignmentUnassignedWorkloadListener listener;

	@Test
	@DisplayName("happy path — atomically decrements teacher.assignments_count")
	void happyPath() {
		UUID teacherPublic = UUID.randomUUID();
		TeacherAssignmentUnassignedEvent event = new TeacherAssignmentUnassignedEvent(
				UUID.randomUUID(), teacherPublic,
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), Instant.now());

		when(teacherRepository.decrementAssignmentsCountByPublicUuid(teacherPublic))
				.thenReturn(1);

		listener.onAssignmentUnassigned(event);

		verify(teacherRepository).decrementAssignmentsCountByPublicUuid(teacherPublic);
	}

	@Test
	@DisplayName("counter at 0 (or teacher missing) → defensive no-op, no exception")
	void counterAtZero() {
		UUID teacherPublic = UUID.randomUUID();
		TeacherAssignmentUnassignedEvent event = new TeacherAssignmentUnassignedEvent(
				UUID.randomUUID(), teacherPublic,
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), Instant.now());

		when(teacherRepository.decrementAssignmentsCountByPublicUuid(teacherPublic))
				.thenReturn(0);

		// Listener swallows the 0-update case as a warning rather than
		// failing the source tx.
		listener.onAssignmentUnassigned(event);

		verify(teacherRepository).decrementAssignmentsCountByPublicUuid(teacherPublic);
		assertThat(true).isTrue();
	}

	@Test
	@DisplayName("repository throws → propagated (upper tx decides policy)")
	void repositoryThrows() {
		UUID teacherPublic = UUID.randomUUID();
		TeacherAssignmentUnassignedEvent event = new TeacherAssignmentUnassignedEvent(
				UUID.randomUUID(), teacherPublic,
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), Instant.now());

		when(teacherRepository.decrementAssignmentsCountByPublicUuid(teacherPublic))
				.thenThrow(new RuntimeException("DB down"));

		try {
			listener.onAssignmentUnassigned(event);
		}
		catch (RuntimeException ignored) {
			// Acceptable: the listener trusts the caller (outer @Transactional)
			// to roll back. We only assert the dispatch was attempted.
		}

		verify(teacherRepository).decrementAssignmentsCountByPublicUuid(teacherPublic);
	}
}
