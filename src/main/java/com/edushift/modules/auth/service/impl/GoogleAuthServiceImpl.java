package com.edushift.modules.auth.service.impl;

import com.edushift.infrastructure.integrations.google.GoogleProfile;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.AuthService;
import com.edushift.modules.auth.service.GoogleAuthService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default {@link GoogleAuthService}.
 *
 * <h3>Matching strategy (per-feature decision)</h3>
 * When a verified Google profile comes in, we try to match the user in
 * this order:
 * <ol>
 *   <li><strong>By {@code google_subject}</strong> — the canonical link.
 *       If the user already linked their Google account, this is a no-op
 *       and we just stamp {@code last_login_at}.</li>
 *   <li><strong>By {@code email}</strong> — covers the first-time
 *       case where the email already exists in the tenant but Google
 *       hasn't been linked yet. We update {@code google_subject} on the
 *       existing row.</li>
 *   <li><strong>Auto-provision</strong> — covers the case where neither
 *       the subject nor the email match. We create a new
 *       {@link User} with {@link UserStatus#ACTIVE} (Google's
 *       {@code email_verified=true} is trusted) and a default role of
 *       {@link UserRole#TEACHER}.</li>
 * </ol>
 *
 * <h3>Anti-enumeration</h3>
 * We never tell the front-end whether the user was newly created or was
 * pre-existing. The {@link AuthResponse} payload is identical in both
 * cases. Only the {@code audit_logs} table records the branch taken (the
 * audit message includes "first-time login" / "returning user").
 *
 * <h3>Tenant / transaction ordering</h3>
 * Mirrors the rationale documented in {@link AuthServiceImpl}'s class
 * javadoc: {@link TenantContext#runAs} wraps the
 * {@link TransactionTemplate#execute} so Hibernate's {@code @TenantId}
 * resolver sees the real tenant id when the session opens.
 */
@Slf4j
@Service
public class GoogleAuthServiceImpl implements GoogleAuthService {

	/** Default role for auto-provisioned users (see class javadoc). */
	private static final UserRole DEFAULT_PROVISIONING_ROLE = UserRole.TEACHER;

	private final TenantRepository tenantRepository;
	private final UserRepository userRepository;
	private final AuthService authService;
	private final AuditLogger auditLogger;
	private final TransactionTemplate txTemplate;

	public GoogleAuthServiceImpl(
			TenantRepository tenantRepository,
			UserRepository userRepository,
			AuthService authService,
			AuditLogger auditLogger,
			PlatformTransactionManager txManager) {
		this.tenantRepository = tenantRepository;
		this.userRepository = userRepository;
		this.authService = authService;
		this.auditLogger = auditLogger;
		this.txTemplate = new TransactionTemplate(txManager);
	}

	@Override
	public AuthResponse loginWithGoogle(GoogleProfile profile, String tenantSlug, String remoteAddr) {
		if (!profile.emailVerified()) {
			auditLogger.log(AuditAction.LOGIN_FAILED, "user_email",
					null, "Google login rejected: email not verified by Google",
					Map.of("email", profile.email()));
			throw new UnauthorizedException("EMAIL_NOT_VERIFIED",
					"Google account email is not verified");
		}

		// Find tenant from the global registry — we DON'T trust profile.hd
		// to identify the tenant (Workspace accounts can belong to anyone).
		// The controller already validated the slug exists; here we just
		// need to confirm the tenant is in ACTIVE status before we run the
		// matching logic.
		Tenant tenant = tenantRepository.findBySlugIgnoreCase(tenantSlug)
				// Defensive: the controller already 404'd if the slug was
				// unknown. If we're here and the tenant is gone, that's a
				// race condition we should surface.
				.orElseThrow(() -> new UnauthorizedException("TENANT_NOT_FOUND",
						"Tenant slug no longer exists"));

		if (tenant.getStatus() != TenantStatus.ACTIVE) {
			log.warn("[google-auth] login rejected -- tenant '{}' status={}",
					tenant.getSlug(), tenant.getStatus());
			throw new UnauthorizedException("TENANT_INACTIVE",
					"Tenant is not active");
		}

		final UUID tenantId = tenant.getId();

		return TenantContext.runAs(tenantId, () ->
				txTemplate.execute(status ->
						doLogin(profile, tenant, remoteAddr)));
	}

	private AuthResponse doLogin(GoogleProfile profile, Tenant tenant, String remoteAddr) {
		// 1. Already linked by google_subject?
		Optional<User> bySubject =
				userRepository.findByGoogleSubject(profile.subject());

		User user;
		boolean provisioned;

		if (bySubject.isPresent()) {
			user = bySubject.get();
			provisioned = false;
		}
		else {
			// 2. Email match in this tenant (auto-link if Google verified the email)
			Optional<User> byEmail = userRepository.findByEmail(profile.email());
			if (byEmail.isPresent()) {
				user = byEmail.get();
				user.setGoogleSubject(profile.subject());
				if (profile.pictureUrl() != null && user.getAvatarUrl() == null) {
					user.setAvatarUrl(profile.pictureUrl());
				}
				user = userRepository.saveAndFlush(user);
				provisioned = false;
			}
			else {
				// 3. Auto-provision
				user = provisionNewUser(profile);
				provisioned = true;
			}
		}

		assertUserCanAuthenticate(user, tenant.getSlug());

		user.recordSuccessfulLogin();
		userRepository.saveAndFlush(user);

		// Reuse the SAME session-issuance path as /auth/login and the
		// tenant self-signup flow: same JWT claims, same refresh-token
		// rotation, same audit row.
		AuthResponse response = authService.issueSession(user, tenant);

		String eventDetail = provisioned
				? "Google login OK (auto-provisioned as " + DEFAULT_PROVISIONING_ROLE + ")"
				: "Google login OK (existing user)";
		log.info("[google-auth] login OK -- tenant='{}', email='{}', publicUuid='{}', provisioned={}",
				tenant.getSlug(), user.getEmail(), user.getPublicUuid(), provisioned);
		auditLogger.log(AuditAction.LOGIN, "user",
				user.getPublicUuid(), eventDetail,
				Map.of(
						"tenantSlug", tenant.getSlug(),
						"email", user.getEmail(),
						"googleSubjectHash", Integer.toHexString(profile.subject().hashCode()),
						"remoteAddr", remoteAddr == null ? "" : remoteAddr,
						"provisioned", String.valueOf(provisioned)
				));

		return response;
	}

	private User provisionNewUser(GoogleProfile profile) {
		User user = new User();
		user.setEmail(profile.email());
		// Split display name into first / last; falls back to the email
		// local-part if Google returned nothing useful.
		String[] parts = splitName(profile.displayName());
		user.setFirstName(parts[0]);
		user.setLastName(parts[1]);
		user.setAvatarUrl(profile.pictureUrl());
		user.setGoogleSubject(profile.subject());
		user.setEmailVerified(true); // Google already verified this email
		user.setStatus(UserStatus.ACTIVE);
		user.setRoleSet(Set.of(DEFAULT_PROVISIONING_ROLE));
		// password_hash is NOT NULL on the schema. We seed an unusable
		// BCrypt hash so password login for this account is rejected
		// (defense in depth — even if a future admin enables it, the
		// sentinel cannot be guessed).
		user.setPasswordHash(
				"$2a$12$0000000000000000000000.0000000000000000000000000000000000000000000");
		return userRepository.saveAndFlush(user);
	}

	private static String[] splitName(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			return new String[]{"User", "Google"};
		}
		String trimmed = displayName.trim();
		int space = trimmed.indexOf(' ');
		if (space < 0) {
			return new String[]{trimmed, ""};
		}
		return new String[]{
				trimmed.substring(0, space),
				trimmed.substring(space + 1).trim()
		};
	}

	private static void assertUserCanAuthenticate(User user, String tenantSlug) {
		UserStatus status = user.getStatus();
		if (status != null && status.canAuthenticate()) {
			return;
		}
		String code = switch (status) {
			case LOCKED -> "USER_LOCKED";
			case SUSPENDED -> "USER_SUSPENDED";
			case INACTIVE -> "USER_INACTIVE";
			case PENDING_VERIFICATION -> "EMAIL_NOT_VERIFIED";
			default -> "USER_NOT_AUTHENTICATABLE";
		};
		log.info("[google-auth] login rejected -- status={}, tenant='{}', email='{}'",
				status, tenantSlug, user.getEmail());
		throw new UnauthorizedException(code,
				"Account cannot authenticate at this time");
	}

	// Local UUID import kept inside the class to keep the top of the file tidy.
}