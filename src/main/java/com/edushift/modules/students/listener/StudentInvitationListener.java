package com.edushift.modules.students.listener;

import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.students.service.impl.StudentServiceImpl;
import com.edushift.modules.users.events.InvitationAcceptedEvent;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Links a freshly-created STUDENT user to its originating student when
 * the invitation carried {@code metadata.studentId}. Synchronous so the
 * write participates in the same transaction as accept; stale profile
 * metadata aborts acceptance so no unlinked account is created.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentInvitationListener {

	private final StudentRepository studentRepository;
	private final UserRepository userRepository;
	private final AuditLogger auditLogger;

	@EventListener
	public void onInvitationAccepted(InvitationAcceptedEvent event) {
		Object raw = event.metadata().get(StudentServiceImpl.METADATA_STUDENT_ID_KEY);
		if (raw == null) {
			return;
		}

		UUID studentInternalId = parseUuid(raw);
		if (studentInternalId == null) {
			throw new BusinessException("PROFILE_INVITATION_INVALID",
					"The student invitation contains invalid profile metadata");
		}

		Student student = studentRepository.findById(studentInternalId).orElse(null);
		if (student == null) {
			throw new ResourceNotFoundException("Student", studentInternalId);
		}

		if (student.getUserId() != null) {
			throw new ConflictException("STUDENT_ALREADY_HAS_USER",
					"The student was linked to another account before this invitation was accepted");
		}

		student.setUserId(event.userPublicUuid());
		studentRepository.save(student);
		userRepository.findByPublicUuid(event.userPublicUuid()).ifPresent(user -> {
			String oldFullName = user.fullName();
			user.setFirstName(student.getFirstName());
			user.setLastName(student.getLastName());
			user.setSecondLastName(student.getSecondLastName());
			userRepository.save(user);
			if (!oldFullName.equals(user.fullName())) {
				auditLogger.log(AuditAction.UPDATE, "user", user.getPublicUuid(),
						"Account identity synchronized from student during invitation acceptance",
						java.util.Map.of("studentPublicUuid", student.getPublicUuid().toString()),
						"students");
			}
		});
		log.info("[students.invitations] linked student={} to user={} via invitation={}",
				student.getPublicUuid(), event.userPublicUuid(),
				event.invitationPublicUuid());
	}

	private static UUID parseUuid(Object raw) {
		try {
			return UUID.fromString(String.valueOf(raw));
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
