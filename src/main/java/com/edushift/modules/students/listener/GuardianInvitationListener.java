package com.edushift.modules.students.listener;

import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.repository.GuardianRepository;
import com.edushift.modules.students.service.impl.StudentGuardianServiceImpl;
import com.edushift.modules.users.events.InvitationAcceptedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Links a freshly-created PARENT user to its originating Guardian when
 * the invitation carried {@code metadata.guardianId}. One Guardian, N
 * children: the listener does not touch {@code student_guardians}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardianInvitationListener {

	private final GuardianRepository guardianRepository;

	@EventListener
	public void onInvitationAccepted(InvitationAcceptedEvent event) {
		Object raw = event.metadata().get(StudentGuardianServiceImpl.METADATA_GUARDIAN_ID_KEY);
		if (raw == null) {
			return;
		}

		UUID guardianInternalId = parseUuid(raw);
		if (guardianInternalId == null) {
			log.warn("[guardians.invitations] ignored event {}: bad guardianId metadata={}",
					event.invitationPublicUuid(), raw);
			return;
		}

		Guardian guardian = guardianRepository.findById(guardianInternalId).orElse(null);
		if (guardian == null) {
			log.warn("[guardians.invitations] event {} carried guardianId={} but no Guardian row found",
					event.invitationPublicUuid(), guardianInternalId);
			return;
		}

		if (guardian.getUserId() != null) {
			log.warn("[guardians.invitations] event {}: guardian {} already linked to user {}; not overwriting with {}",
					event.invitationPublicUuid(), guardian.getPublicUuid(),
					guardian.getUserId(), event.userPublicUuid());
			return;
		}

		guardian.setUserId(event.userPublicUuid());
		guardianRepository.save(guardian);
		log.info("[guardians.invitations] linked guardian={} to user={} via invitation={}",
				guardian.getPublicUuid(), event.userPublicUuid(),
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
