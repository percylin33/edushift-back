package com.edushift.modules.teachers.listener;

import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.modules.teachers.service.impl.TeacherServiceImpl;
import com.edushift.modules.users.events.InvitationAcceptedEvent;
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
 * teacher does not exist, the event is ignored (defensive against
 * stale invitations whose teacher was deleted before accept).</p>
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
			log.warn("[teachers.invitations] ignored event {}: bad teacherId metadata={}",
					event.invitationPublicUuid(), raw);
			return;
		}

		Teacher teacher = teacherRepository.findById(teacherInternalId).orElse(null);
		if (teacher == null) {
			// The teacher might have been deleted between invite and accept;
			// the new user still gets created (their account is valid) but
			// we leave it unlinked. Logged at warn so admins notice.
			log.warn("[teachers.invitations] event {} carried teacherId={} but no Teacher row found in current tenant",
					event.invitationPublicUuid(), teacherInternalId);
			return;
		}

		// Defensive: if some other flow already linked the teacher, do not
		// overwrite. This shouldn't happen given the invariants but the
		// failure mode of a silent overwrite would be hard to debug.
		if (teacher.getUserId() != null) {
			log.warn("[teachers.invitations] event {}: teacher {} already linked to user {}; not overwriting with {}",
					event.invitationPublicUuid(), teacher.getPublicUuid(),
					teacher.getUserId(), event.userPublicUuid());
			return;
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
