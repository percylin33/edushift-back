package com.edushift.modules.students.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.students.service.impl.StudentServiceImpl;
import com.edushift.modules.users.events.InvitationAcceptedEvent;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentInvitationListenerTest {

	@Mock private StudentRepository studentRepository;
	@Mock private UserRepository userRepository;
	@Mock private AuditLogger auditLogger;

	@InjectMocks private StudentInvitationListener listener;

	@Test
	void missingStudentRejectsInvitationAcceptance() {
		UUID studentId = UUID.randomUUID();
		when(studentRepository.findById(studentId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> listener.onInvitationAccepted(event(studentId)))
				.isInstanceOf(ResourceNotFoundException.class);

		verify(studentRepository, never()).save(any());
	}

	@Test
	void alreadyLinkedStudentRejectsInvitationAcceptanceWithoutOverwrite() {
		UUID studentId = UUID.randomUUID();
		UUID existingUserId = UUID.randomUUID();
		Student student = new Student();
		student.setUserId(existingUserId);
		when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));

		assertThatThrownBy(() -> listener.onInvitationAccepted(event(studentId)))
				.isInstanceOf(ConflictException.class);

		verify(studentRepository, never()).save(any());
		verify(userRepository, never()).findByPublicUuid(any());
	}

	private static InvitationAcceptedEvent event(UUID studentId) {
		return new InvitationAcceptedEvent(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(),
				Map.of(StudentServiceImpl.METADATA_STUDENT_ID_KEY, studentId.toString()),
				Instant.now());
	}
}
