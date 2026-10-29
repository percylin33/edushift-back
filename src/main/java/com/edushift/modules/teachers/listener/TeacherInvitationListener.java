package com.edushift.modules.teachers.listener;

import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.modules.teachers.service.impl.TeacherServiceImpl;
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
 * Reacts to {@link InvitationAcceptedEvent} and links a freshly-created
 * user to its originating teacher when the invitation carried
 * {@code metadata.teacherId}.
 *
 * <p>This is a synchronous {@code @EventListener} — Spring publishes
 * the event inside the same Hibernate session as the user creation
 * (see {@code UserInvitationServiceImpl.acceptInvitation}), so the
 * teacher mutation here participates in the same transaction. If the
 * teacher was deleted or linked after the invitation was sent, acceptance
 * fails and the account creation rolls back instead of leaving an unlinked user.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeacherInvitationListener {

	private final TeacherRepository teacherRepository;

	@EventListener
	public void onInvitationAccepted(InvitationAcceptedEvent event) {
		Object raw = event.metadata().get(TeacherServiceImpl.METADATA_TEACHER_ID_KEY);
		if (raw == null) return;

		UUID teacherInternalId = parseUuid(raw);
		if (teacherInternalId == null) {
			throw new BusinessException("PROFILE_INVITATION_INVALID",
					"The teacher invitation contains invalid profile metadata");
		}

		Teacher teacher = teacherRepository.findById(teacherInternalId).orElse(null);
		if (teacher == null) {
			throw new ResourceNotFoundException("Teacher", teacherInternalId);
		}

		// Defensive: if some other flow already linked the teacher, do not
		// overwrite. This shouldn't happen given the invariants but the
		// failure mode of a silent overwrite would be hard to debug.
		if (teacher.getUserId() != null) {
			throw new ConflictException("TEACHER_ALREADY_HAS_USER",
					"The teacher was linked to another account before this invitation was accepted");
		}

		// DEBT-FK-BUGS-3 / V77: teachers.user_id stores public_uuid now.
		// The event carries both the internal id (event.userId()) and the
		// publicUuid (event.userPublicUuid()) — we use the publicUuid.
		teacher.setUserId(event.userPublicUuid());
		teacherRepository.save(teacher);
		log.info("[teachers.invitations] linked teacher={} to user={} via invitation={}",
				teacher.getPublicUuid(), event.userPublicUuid(),
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
