package com.edushift.modules.users.service.impl;

import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.AuthService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.modules.users.dto.AcceptInvitationRequest;
import com.edushift.modules.users.dto.CreateInvitationRequest;
import com.edushift.modules.users.dto.InvitationPreflightResponse;
import com.edushift.modules.users.dto.InvitationResponse;
import com.edushift.modules.users.entity.InvitationStatus;
import com.edushift.modules.users.entity.UserInvitation;
import com.edushift.modules.users.mapper.UserInvitationMapper;
import com.edushift.modules.users.repository.UserInvitationRepository;
import com.edushift.modules.users.service.UserInvitationService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GoneException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default {@link UserInvitationService}.
 *
 * <h3>Two transaction shapes</h3>
 * <ul>
 *   <li><strong>Tenant-scoped admin paths</strong> ({@code create / list /
 *       cancel}) run as plain Spring {@code @Transactional} methods —
 *       {@code TenantContext} is already set by the JWT filter.</li>
 *   <li><strong>Public accept path</strong> spans <em>two</em>
 *       transactions: an outer global lookup of the invitation by
 *       token (no tenant context), then an inner
 *       {@link TenantContext#runAs} block that creates the user inside
 *       the invitation's tenant. Same shape as
 *       {@code TenantServiceImpl.register}.</li>
 * </ul>
 *
 * <h3>Token entropy</h3>
 * Tokens are 32 url-safe-base64 chars derived from 24 random bytes
 * (~192 bits of entropy). Output fits the {@code varchar(64)} column
 * with room to spare and the partial unique index makes a collision
 * surface as a clean DB error rather than a silent overwrite.
 */
@Slf4j
@Service
public class UserInvitationServiceImpl implements UserInvitationService {

	/** How long a freshly-issued invitation is valid for. */
	static final Duration TOKEN_TTL = Duration.ofDays(7);

	private final UserInvitationRepository invitationRepository;
	private final UserInvitationMapper invitationMapper;
	private final UserRepository userRepository;
	private final TenantRepository tenantRepository;
	private final AuthService authService;
	private final PasswordEncoder passwordEncoder;
	private final TransactionTemplate txTemplate;
	private final SecureRandom secureRandom = new SecureRandom();

	public UserInvitationServiceImpl(
			UserInvitationRepository invitationRepository,
			UserInvitationMapper invitationMapper,
			UserRepository userRepository,
			TenantRepository tenantRepository,
			AuthService authService,
			PasswordEncoder passwordEncoder,
			PlatformTransactionManager txManager
	) {
		this.invitationRepository = invitationRepository;
		this.invitationMapper = invitationMapper;
		this.userRepository = userRepository;
		this.tenantRepository = tenantRepository;
		this.authService = authService;
		this.passwordEncoder = passwordEncoder;
		this.txTemplate = new TransactionTemplate(txManager);
	}

	// ===========================================================================
	// Admin paths
	// ===========================================================================

	@Override
	@Transactional
	public InvitationResponse createInvitation(CreateInvitationRequest request) {
		Instant now = Instant.now();
		String email = request.email().trim().toLowerCase();

		// Validate roles up-front so we never persist a row that references
		// an unknown role enum.
		Set<UserRole> roles = parseRoles(request.roles());

		// Pre-check: a single email can have at most one active pending
		// invitation per tenant. The DB partial unique index is the
		// belt-and-suspenders guarantee; the pre-check makes the error
		// message actionable instead of a generic constraint violation.
		invitationRepository.findActivePendingByEmail(email, now).ifPresent(existing -> {
			throw new ConflictException("INVITATION_ALREADY_PENDING",
					"An active invitation already exists for " + email);
		});

		UserInvitation invitation = new UserInvitation();
		invitation.setEmail(email);
		invitation.setFirstName(request.firstName().trim());
		invitation.setLastName(request.lastName().trim());
		invitation.setRoleNames(roles.stream().map(UserRole::name)
				.collect(Collectors.toCollection(LinkedHashSet::new)));
		invitation.setToken(generateToken());
		invitation.setExpiresAt(now.plus(TOKEN_TTL));

		UserInvitation saved = invitationRepository.save(invitation);
		log.info("[invitations] created -- publicUuid={} email='{}' expires={}",
				saved.getPublicUuid(), saved.getEmail(), saved.getExpiresAt());
		return invitationMapper.toResponseWithToken(saved, now);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<InvitationResponse> listPendingInvitations(Pageable pageable) {
		Instant now = Instant.now();
		return invitationRepository.findPendingInTenant(now, pageable)
				.map(inv -> invitationMapper.toResponse(inv, now));
	}

	@Override
	@Transactional
	public InvitationResponse cancelInvitation(UUID publicUuid) {
		Instant now = Instant.now();
		UserInvitation invitation = invitationRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Invitation", publicUuid));

		if (invitation.isAccepted()) {
			// The user is already in the system; cancelling now would be
			// theatrical. Surface the state explicitly so the UI doesn't
			// pretend the operation worked.
			throw new ConflictException("INVITATION_ALREADY_ACCEPTED",
					"Invitation has already been accepted; cannot cancel");
		}

		if (invitation.isCancelled()) {
			log.debug("[invitations] cancel -- {} already cancelled; no-op",
					invitation.getPublicUuid());
			return invitationMapper.toResponse(invitation, now);
		}

		invitation.markCancelled(now);
		UserInvitation saved = invitationRepository.save(invitation);
		log.info("[invitations] cancelled -- publicUuid={} email='{}'",
				saved.getPublicUuid(), saved.getEmail());
		return invitationMapper.toResponse(saved, now);
	}

	// ===========================================================================
	// Public paths (token-driven, run without TenantContext)
	// ===========================================================================

	@Override
	@Transactional(readOnly = true)
	public InvitationPreflightResponse getPreflight(String token) {
		Instant now = Instant.now();
		UserInvitation invitation = loadByTokenOrFail(token, now);

		// Resolve tenant by id (a global lookup; the invitation carries
		// the tenantId because TenantAwareEntity persisted it on insert).
		Tenant tenant = tenantRepository.findById(invitation.getTenantId())
				.orElseThrow(() -> new ResourceNotFoundException("Tenant", invitation.getTenantId()));

		return new InvitationPreflightResponse(
				invitation.getEmail(),
				invitation.getFirstName(),
				invitation.getLastName(),
				tenant.getName()
		);
	}

	@Override
	public AuthResponse acceptInvitation(AcceptInvitationRequest request) {
		final Instant now = Instant.now();

		// Step 1 — global lookup + lifecycle gating, no tenant context yet.
		UserInvitation invitation = loadByTokenOrFail(request.token(), now);

		Tenant tenant = tenantRepository.findById(invitation.getTenantId())
				.orElseThrow(() -> new ResourceNotFoundException("Tenant", invitation.getTenantId()));

		// Step 2 — switch to the invitation's tenant context to create
		// the user + issue the session inside its scope.
		final UUID tenantId = tenant.getId();
		final String email = invitation.getEmail();
		final String passwordHash = passwordEncoder.encode(request.password());
		final UUID invitationId = invitation.getId();

		return TenantContext.runAs(tenantId, () -> txTemplate.execute(status -> {
			User newUser = new User();
			newUser.setEmail(email);
			newUser.setFirstName(invitation.getFirstName());
			newUser.setLastName(invitation.getLastName());
			newUser.setPasswordHash(passwordHash);
			newUser.setStatus(UserStatus.ACTIVE);
			newUser.setEmailVerified(true);   // accepting via the token IS the verification step
			newUser.setMfaEnabled(false);
			for (String roleName : invitation.getRoleNames()) {
				UserRole role = UserRole.fromName(roleName);
				if (role != null) {
					newUser.addRole(role);
				}
			}
			User savedUser = userRepository.saveAndFlush(newUser);

			// Re-load the invitation inside the transactional context and
			// mark it accepted. We re-load instead of reusing the outer
			// reference so JPA's persistence context manages the update.
			UserInvitation tracked = invitationRepository.findById(invitationId)
					.orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));
			tracked.markAccepted(now);
			invitationRepository.save(tracked);

			log.info("[invitations] accepted -- publicUuid={} userId={} tenant={}",
					tracked.getPublicUuid(), savedUser.getPublicUuid(), tenantId);

			return authService.issueSession(savedUser, tenant);
		}));
	}

	// ===========================================================================
	// Internals
	// ===========================================================================

	private UserInvitation loadByTokenOrFail(String token, Instant now) {
		UserInvitation invitation = invitationRepository.findActiveByToken(token)
				.orElseThrow(() -> new ResourceNotFoundException("Invitation", "<by token>"));

		if (invitation.isAccepted()) {
			throw new GoneException("INVITATION_ALREADY_ACCEPTED",
					"This invitation has already been accepted");
		}
		if (invitation.isCancelled()) {
			throw new GoneException("INVITATION_CANCELLED",
					"This invitation has been cancelled by an administrator");
		}
		if (invitation.isExpired(now)) {
			throw new GoneException("INVITATION_EXPIRED",
					"This invitation has expired; please request a new one");
		}
		return invitation;
	}

	private Set<UserRole> parseRoles(Set<String> names) {
		Set<UserRole> resolved = new LinkedHashSet<>();
		for (String name : names) {
			UserRole role = UserRole.fromName(name);
			if (role == null) {
				throw new BusinessException("INVALID_ROLE",
						"Unknown role: '" + name + "'");
			}
			resolved.add(role);
		}
		return resolved;
	}

	private String generateToken() {
		// 24 random bytes → 32 url-safe-base64 chars (no padding).
		byte[] raw = new byte[24];
		secureRandom.nextBytes(raw);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
	}

	// Exposed for tests that need to compute the derived status without
	// going through the mapper.
	@SuppressWarnings("unused")
	private InvitationStatus deriveStatus(UserInvitation invitation, Instant now) {
		return invitationMapper.deriveStatus(invitation, now);
	}
}
