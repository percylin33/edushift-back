package com.edushift.modules.students.listener;

import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.students.service.impl.StudentServiceImpl;
import com.edushift.modules.users.events.InvitationAcceptedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Links a freshly-created STUDENT user to its originating student when
 * the invitation carried {@code metadata.studentId}. Synchronous so the
 * write participates in the same transaction as accept.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentInvitationListener {

	private final StudentRepository studentRepository;

	@EventListener
	public void onInvitationAccepted(InvitationAcceptedEvent event) {
		Object raw = event.metadata().get(StudentServiceImpl.METADATA_STUDENT_ID_KEY);
		if (raw == null) {
			return;
		}

		UUID studentInternalId = parseUuid(raw);
		if (studentInternalId == null) {
			log.warn("[students.invitations] ignored event {}: bad studentId metadata={}",
					event.invitationPublicUuid(), raw);
			return;
		}

		Student student = studentRepository.findById(studentInternalId).orElse(null);
		if (student == null) {
			log.warn("[students.invitations] event {} carried studentId={} but no Student row found",
					event.invitationPublicUuid(), studentInternalId);
			return;
		}

		if (student.getUserId() != null) {
			log.warn("[students.invitations] event {}: student {} already linked to user {}; not overwriting with {}",
					event.invitationPublicUuid(), student.getPublicUuid(),
					student.getUserId(), event.userPublicUuid());
			return;
		}

		student.setUserId(event.userPublicUuid());
		studentRepository.save(student);
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
