package com.edushift.modules.admin.invitations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GoneException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchoolInvitationServiceImplTest {

	@Mock private SchoolInvitationRepository repository;
	@Mock private SchoolInvitationMailer mailer;

	private SchoolInvitationServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new SchoolInvitationServiceImpl(repository, mailer);
		when(mailer.setupUrl(anyString())).thenAnswer(inv -> "http://localhost:4201/auth/setup-school?token=" + inv.getArgument(0));
		when(mailer.send(anyString(), anyString(), any())).thenReturn(true);
	}

	@Test
	@DisplayName("create persists a pending invite and returns the setup URL")
	void createHappyPath() {
		when(repository.findActivePendingByEmail(eq("founder@school.test"), any(Instant.class)))
				.thenReturn(Optional.empty());
		when(repository.save(any(SchoolInvitation.class))).thenAnswer(inv -> {
			SchoolInvitation i = inv.getArgument(0);
			i.setPublicUuid(UUID.randomUUID());
			return i;
		});

		SchoolInvitationResponse response = service.create(new CreateSchoolInvitationRequest("Founder@School.test"));

		assertThat(response.email()).isEqualTo("founder@school.test");
		assertThat(response.token()).isNotBlank();
		assertThat(response.setupUrl()).contains("/auth/setup-school?token=");
		assertThat(response.emailSent()).isTrue();
		ArgumentCaptor<SchoolInvitation> captor = ArgumentCaptor.forClass(SchoolInvitation.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getEmail()).isEqualTo("founder@school.test");
	}

	@Test
	@DisplayName("create rejects a second pending invite for the same email")
	void createDuplicatePending() {
		SchoolInvitation existing = new SchoolInvitation();
		existing.setEmail("founder@school.test");
		when(repository.findActivePendingByEmail(eq("founder@school.test"), any(Instant.class)))
				.thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> service.create(new CreateSchoolInvitationRequest("founder@school.test")))
				.isInstanceOfSatisfying(ConflictException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("SCHOOL_INVITATION_ALREADY_PENDING"));
	}

	@Test
	@DisplayName("requirePending maps an expired invite to 410")
	void expiredTokenIsGone() {
		SchoolInvitation invitation = new SchoolInvitation();
		invitation.setEmail("founder@school.test");
		invitation.setExpiresAt(Instant.now().minusSeconds(60));
		when(repository.findByToken("expiredtoken123456")).thenReturn(Optional.of(invitation));

		assertThatThrownBy(() -> service.requirePending("expiredtoken123456"))
				.isInstanceOf(GoneException.class);
	}
}
