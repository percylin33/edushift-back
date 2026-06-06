package com.edushift.modules.users.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.dto.UserSummary;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.AuthService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.modules.users.dto.AcceptInvitationRequest;
import com.edushift.modules.users.dto.CreateInvitationRequest;
import com.edushift.modules.users.dto.InvitationPreflightResponse;
import com.edushift.modules.users.dto.InvitationResponse;
import com.edushift.modules.users.entity.InvitationStatus;
import com.edushift.modules.users.entity.UserInvitation;
import com.edushift.modules.users.mapper.UserInvitationMapper;
import com.edushift.modules.users.repository.UserInvitationRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GoneException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class UserInvitationServiceImplTest {

	@Mock private UserInvitationRepository invitationRepository;
	@Mock private UserRepository userRepository;
	@Mock private TenantRepository tenantRepository;
	@Mock private AuthService authService;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
	@Mock private PlatformTransactionManager txManager;

	private UserInvitationServiceImpl service;

	private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@BeforeEach
	void setUp() {
		service = new UserInvitationServiceImpl(
				invitationRepository, new UserInvitationMapper(),
				userRepository, tenantRepository, authService,
				passwordEncoder, eventPublisher, txManager);
		TenantContext.set(TENANT_ID);
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	// ===========================================================================
	// createInvitation
	// ===========================================================================

	@Nested
	@DisplayName("createInvitation")
	class CreateInvitation {

		@Test
		@DisplayName("happy path — persists, generates a token, returns full projection")
		void happyPath() {
			when(invitationRepository.findActivePendingByEmail(eq("teach@acme.test"), any(Instant.class)))
					.thenReturn(Optional.empty());
			when(invitationRepository.save(any(UserInvitation.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			CreateInvitationRequest request = new CreateInvitationRequest(
					"  Teach@acme.test  ", "Teach", "Doe", Set.of("TEACHER"));

			InvitationResponse response = service.createInvitation(request);

			assertThat(response.email()).isEqualTo("teach@acme.test");
			assertThat(response.token()).isNotBlank();
			assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
			assertThat(response.roles()).containsExactly("TEACHER");

			ArgumentCaptor<UserInvitation> captor = ArgumentCaptor.forClass(UserInvitation.class);
			verify(invitationRepository).save(captor.capture());
			UserInvitation persisted = captor.getValue();
			assertThat(persisted.getEmail()).isEqualTo("teach@acme.test");
			assertThat(persisted.getExpiresAt()).isAfter(Instant.now());
			assertThat(persisted.getToken()).hasSize(32); // url-safe base64 of 24 bytes
		}

		@Test
		@DisplayName("email already has active pending → ConflictException(INVITATION_ALREADY_PENDING)")
		void duplicatePending() {
			UserInvitation existing = newPendingInvitation("dup@acme.test");
			when(invitationRepository.findActivePendingByEmail(eq("dup@acme.test"), any(Instant.class)))
					.thenReturn(Optional.of(existing));

			CreateInvitationRequest request = new CreateInvitationRequest(
					"dup@acme.test", "Dup", "User", Set.of("TEACHER"));

			assertThatThrownBy(() -> service.createInvitation(request))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("INVITATION_ALREADY_PENDING"));

			verify(invitationRepository, never()).save(any());
		}

		@Test
		@DisplayName("unknown role → BusinessException(INVALID_ROLE), validated before duplicate-check")
		void unknownRoleRejected() {
			// parseRoles runs before the email duplicate check, so we never
			// hit the repository — assert that explicitly with `verifyNoInteractions`
			// (this also documents the intentional ordering).
			CreateInvitationRequest request = new CreateInvitationRequest(
					"new@acme.test", "New", "User", Set.of("WIZARD"));

			assertThatThrownBy(() -> service.createInvitation(request))
					.isInstanceOfSatisfying(BusinessException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("INVALID_ROLE"));

			verify(invitationRepository, never()).save(any());
			verify(invitationRepository, never())
					.findActivePendingByEmail(anyString(), any(Instant.class));
		}
	}

	// ===========================================================================
	// listPendingInvitations
	// ===========================================================================

	@Nested
	@DisplayName("listPendingInvitations")
	class ListPending {

		@Test
		@DisplayName("delegates to repo + maps each row, tokens are stripped")
		void delegatesAndStripsTokens() {
			UserInvitation a = newPendingInvitation("a@acme.test");
			UserInvitation b = newPendingInvitation("b@acme.test");
			when(invitationRepository.findPendingInTenant(any(Instant.class), any(Pageable.class)))
					.thenReturn(new PageImpl<>(List.of(a, b)));

			Page<InvitationResponse> page = service.listPendingInvitations(Pageable.unpaged());

			assertThat(page.getContent()).hasSize(2);
			assertThat(page.getContent()).allMatch(r -> r.token() == null);
		}
	}

	// ===========================================================================
	// cancelInvitation
	// ===========================================================================

	@Nested
	@DisplayName("cancelInvitation")
	class CancelInvitation {

		@Test
		@DisplayName("happy path — sets cancelledAt and persists")
		void happyPath() {
			UUID publicUuid = UUID.randomUUID();
			UserInvitation invitation = newPendingInvitation("teach@acme.test");
			invitation.setPublicUuid(publicUuid);
			when(invitationRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(invitation));
			when(invitationRepository.save(any(UserInvitation.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			InvitationResponse response = service.cancelInvitation(publicUuid);

			assertThat(response.status()).isEqualTo(InvitationStatus.CANCELLED);
			assertThat(invitation.isCancelled()).isTrue();
		}

		@Test
		@DisplayName("already accepted → ConflictException(INVITATION_ALREADY_ACCEPTED)")
		void alreadyAcceptedRefuses() {
			UUID publicUuid = UUID.randomUUID();
			UserInvitation invitation = newPendingInvitation("teach@acme.test");
			invitation.setPublicUuid(publicUuid);
			invitation.markAccepted(Instant.now().minusSeconds(60));
			when(invitationRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(invitation));

			assertThatThrownBy(() -> service.cancelInvitation(publicUuid))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("INVITATION_ALREADY_ACCEPTED"));
		}

		@Test
		@DisplayName("already cancelled → idempotent no-op (no save)")
		void alreadyCancelledIsIdempotent() {
			UUID publicUuid = UUID.randomUUID();
			UserInvitation invitation = newPendingInvitation("teach@acme.test");
			invitation.setPublicUuid(publicUuid);
			invitation.markCancelled(Instant.now().minusSeconds(60));
			when(invitationRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(invitation));

			service.cancelInvitation(publicUuid);

			verify(invitationRepository, never()).save(any(UserInvitation.class));
		}

		@Test
		@DisplayName("unknown publicUuid → ResourceNotFoundException")
		void unknownThrows() {
			UUID publicUuid = UUID.randomUUID();
			when(invitationRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.cancelInvitation(publicUuid))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// ===========================================================================
	// getPreflight
	// ===========================================================================

	@Nested
	@DisplayName("getPreflight (public)")
	class GetPreflight {

		@Test
		@DisplayName("happy path — returns recipient + tenant name")
		void happyPath() {
			UserInvitation invitation = newPendingInvitation("ada@acme.test");
			Tenant tenant = newTenant("Acme Corp");
			when(invitationRepository.findActiveByToken("tok123")).thenReturn(Optional.of(invitation));
			when(tenantRepository.findById(invitation.getTenantId())).thenReturn(Optional.of(tenant));

			InvitationPreflightResponse response = service.getPreflight("tok123");

			assertThat(response.email()).isEqualTo("ada@acme.test");
			assertThat(response.firstName()).isEqualTo("Ada");
			assertThat(response.tenantName()).isEqualTo("Acme Corp");
		}

		@Test
		@DisplayName("unknown token → ResourceNotFoundException (404)")
		void unknownToken() {
			when(invitationRepository.findActiveByToken("missing")).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.getPreflight("missing"))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("accepted token → GoneException(INVITATION_ALREADY_ACCEPTED)")
		void acceptedToken() {
			UserInvitation invitation = newPendingInvitation("ada@acme.test");
			invitation.markAccepted(Instant.now().minusSeconds(60));
			when(invitationRepository.findActiveByToken("tok")).thenReturn(Optional.of(invitation));

			assertThatThrownBy(() -> service.getPreflight("tok"))
					.isInstanceOfSatisfying(GoneException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("INVITATION_ALREADY_ACCEPTED"));
		}

		@Test
		@DisplayName("cancelled token → GoneException(INVITATION_CANCELLED)")
		void cancelledToken() {
			UserInvitation invitation = newPendingInvitation("ada@acme.test");
			invitation.markCancelled(Instant.now().minusSeconds(30));
			when(invitationRepository.findActiveByToken("tok")).thenReturn(Optional.of(invitation));

			assertThatThrownBy(() -> service.getPreflight("tok"))
					.isInstanceOfSatisfying(GoneException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("INVITATION_CANCELLED"));
		}

		@Test
		@DisplayName("expired token → GoneException(INVITATION_EXPIRED)")
		void expiredToken() {
			UserInvitation invitation = newPendingInvitation("ada@acme.test");
			invitation.setExpiresAt(Instant.now().minusSeconds(1));
			when(invitationRepository.findActiveByToken("tok")).thenReturn(Optional.of(invitation));

			assertThatThrownBy(() -> service.getPreflight("tok"))
					.isInstanceOfSatisfying(GoneException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("INVITATION_EXPIRED"));
		}
	}

	// ===========================================================================
	// acceptInvitation
	// ===========================================================================

	@Nested
	@DisplayName("acceptInvitation (public)")
	class AcceptInvitation {

		@Test
		@DisplayName("happy path — creates user with invited roles + marks invitation accepted + issues session")
		void happyPath() {
			UUID invitationId = UUID.randomUUID();
			UserInvitation invitation = newPendingInvitation("teach@acme.test");
			setIdViaReflection(invitation, invitationId);
			Tenant tenant = newTenant("Acme Corp");
			when(invitationRepository.findActiveByToken("tok")).thenReturn(Optional.of(invitation));
			when(tenantRepository.findById(invitation.getTenantId())).thenReturn(Optional.of(tenant));
			when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
			when(passwordEncoder.encode("Sup3rSecret!")).thenReturn("hashed-pw");
			when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
			when(invitationRepository.save(any(UserInvitation.class))).thenAnswer(inv -> inv.getArgument(0));

			AuthResponse expected = new AuthResponse(
					"access", "refresh", "Bearer", 900L,
					new UserSummary(UUID.randomUUID(), "Teach Doe",
							"teach@acme.test", null, UserStatus.ACTIVE));
			when(authService.issueSession(any(User.class), eq(tenant))).thenReturn(expected);

			AuthResponse session = service.acceptInvitation(
					new AcceptInvitationRequest("tok", "Sup3rSecret!"));

			assertThat(session).isSameAs(expected);
			assertThat(invitation.isAccepted()).isTrue();

			ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
			verify(userRepository, times(1)).saveAndFlush(userCaptor.capture());
			User created = userCaptor.getValue();
			assertThat(created.getEmail()).isEqualTo("teach@acme.test");
			assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
			assertThat(created.isEmailVerified()).isTrue();
			assertThat(created.getRoleNames()).containsExactly("TEACHER");
		}

		@Test
		@DisplayName("expired token → GoneException, no user created")
		void expiredTokenRejected() {
			UserInvitation invitation = newPendingInvitation("teach@acme.test");
			invitation.setExpiresAt(Instant.now().minusSeconds(1));
			when(invitationRepository.findActiveByToken("tok")).thenReturn(Optional.of(invitation));

			assertThatThrownBy(() -> service.acceptInvitation(
					new AcceptInvitationRequest("tok", "Sup3rSecret!")))
					.isInstanceOf(GoneException.class);

			verify(userRepository, never()).saveAndFlush(any(User.class));
			verify(authService, never()).issueSession(any(), any());
		}

		@Test
		@DisplayName("unknown token → ResourceNotFoundException, no DB writes")
		void unknownTokenRejected() {
			when(invitationRepository.findActiveByToken("missing")).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.acceptInvitation(
					new AcceptInvitationRequest("missing", "Sup3rSecret!")))
					.isInstanceOf(ResourceNotFoundException.class);

			verify(userRepository, never()).saveAndFlush(any(User.class));
		}
	}

	// ===========================================================================
	// Fixtures
	// ===========================================================================

	private UserInvitation newPendingInvitation(String email) {
		UserInvitation inv = new UserInvitation();
		setIdViaReflection(inv, UUID.randomUUID());
		inv.setPublicUuid(UUID.randomUUID());
		inv.setTenantId(TENANT_ID);
		inv.setEmail(email);
		inv.setFirstName("Ada");
		inv.setLastName("Lovelace");
		inv.setRoleNames(Set.of("TEACHER"));
		inv.setToken("token-" + UUID.randomUUID());
		inv.setExpiresAt(Instant.now().plusSeconds(60 * 60));
		return inv;
	}

	private static Tenant newTenant(String name) {
		Tenant t = new Tenant();
		setIdViaReflection(t, TENANT_ID);
		t.setPublicUuid(UUID.randomUUID());
		t.setName(name);
		t.setSlug("acme");
		t.setStatus(TenantStatus.ACTIVE);
		return t;
	}

	private static void setIdViaReflection(Object entity, UUID id) {
		try {
			Class<?> clazz = entity.getClass();
			while (clazz != null) {
				try {
					Field f = clazz.getDeclaredField("id");
					f.setAccessible(true);
					f.set(entity, id);
					return;
				}
				catch (NoSuchFieldException ignored) {
					clazz = clazz.getSuperclass();
				}
			}
			throw new IllegalStateException("No 'id' field");
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
