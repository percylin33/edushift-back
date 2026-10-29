package com.edushift.modules.students.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.payments.service.SubscriptionService;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.repository.GuardianRepository;
import com.edushift.modules.students.service.impl.StudentGuardianServiceImpl;
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
class GuardianInvitationListenerTest {

	@Mock private GuardianRepository guardianRepository;
	@Mock private SubscriptionService subscriptionService;

	@InjectMocks private GuardianInvitationListener listener;

	@Test
	void missingGuardianRejectsInvitationAcceptance() {
		UUID guardianId = UUID.randomUUID();
		when(guardianRepository.findById(guardianId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> listener.onInvitationAccepted(event(guardianId)))
				.isInstanceOf(ResourceNotFoundException.class);

		verify(guardianRepository, never()).save(any());
	}

	@Test
	void alreadyLinkedGuardianRejectsInvitationAcceptanceWithoutOverwrite() {
		UUID guardianId = UUID.randomUUID();
		UUID existingUserId = UUID.randomUUID();
		Guardian guardian = new Guardian();
		guardian.setUserId(existingUserId);
		when(guardianRepository.findById(guardianId)).thenReturn(Optional.of(guardian));

		assertThatThrownBy(() -> listener.onInvitationAccepted(event(guardianId)))
				.isInstanceOf(ConflictException.class);

		verify(guardianRepository, never()).save(any());
		verify(subscriptionService, never()).synchronizeGuardianForBillingRules(any());
	}

	private static InvitationAcceptedEvent event(UUID guardianId) {
		return new InvitationAcceptedEvent(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(),
				Map.of(StudentGuardianServiceImpl.METADATA_GUARDIAN_ID_KEY, guardianId.toString()),
				Instant.now());
	}
}
