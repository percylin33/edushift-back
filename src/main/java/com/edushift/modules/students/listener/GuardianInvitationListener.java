package com.edushift.modules.students.listener;

import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.repository.GuardianRepository;
import com.edushift.modules.students.service.impl.StudentGuardianServiceImpl;
import com.edushift.modules.payments.service.SubscriptionService;
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
 * Links a freshly-created PARENT user to its originating Guardian when
 * the invitation carried {@code metadata.guardianId}. One Guardian, N
 * children: the listener does not touch {@code student_guardians}. A stale
 * profile reference aborts acceptance and rolls back the account creation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardianInvitationListener {

	private final GuardianRepository guardianRepository;
	private final SubscriptionService subscriptionService;

	@EventListener
	public void onInvitationAccepted(InvitationAcceptedEvent event) {
		Object raw = event.metadata().get(StudentGuardianServiceImpl.METADATA_GUARDIAN_ID_KEY);
		if (raw == null) {
			return;
		}

		UUID guardianInternalId = parseUuid(raw);
		if (guardianInternalId == null) {
			throw new BusinessException("PROFILE_INVITATION_INVALID",
					"The guardian invitation contains invalid profile metadata");
		}

		Guardian guardian = guardianRepository.findById(guardianInternalId).orElse(null);
		if (guardian == null) {
			throw new ResourceNotFoundException("Guardian", guardianInternalId);
		}

		if (guardian.getUserId() != null) {
			throw new ConflictException("GUARDIAN_ALREADY_HAS_USER",
					"The guardian was linked to another account before this invitation was accepted");
		}

		guardian.setUserId(event.userPublicUuid());
		guardianRepository.save(guardian);
		subscriptionService.synchronizeGuardianForBillingRules(guardian.getId());
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
