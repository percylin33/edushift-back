package com.edushift.modules.auth.service.impl;

import com.edushift.infrastructure.integrations.google.GoogleProfile;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.entity.User;
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
 *   <li><strong>Unknown email</strong> — no auto-provision. The school
 *       must invite the person first (student / guardian / teacher
 *       ficha). Unknown Google emails return
 *       {@code 401 GOOGLE_ACCOUNT_NOT_INVITED} instead of creating a
 *       ghost TEACHER.</li>
 * </ol>
 *
 * <h3>Unknown Google emails</h3>
 * We do not auto-provision. Unknown emails return
 * {@code 401 GOOGLE_ACCOUNT_NOT_INVITED}. The school must invite the
 * person from the student / guardian / teacher ficha first. Existing
 * users (by google_subject or email) still log in as before.
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

		if (bySubject.isPresent()) {
			user = bySubject.get();
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
			}
			else {
				log.info("[google-auth] unknown email '{}' in tenant '{}' — invite required",
						profile.email(), tenant.getSlug());
				throw new UnauthorizedException("GOOGLE_ACCOUNT_NOT_INVITED",
						"No account exists for this Google email. Ask the school to send an invitation.");
			}
		}

		assertUserCanAuthenticate(user, tenant.getSlug());

		user.recordSuccessfulLogin();
		userRepository.saveAndFlush(user);

		// Reuse the SAME session-issuance path as /auth/login and the
		// tenant self-signup flow: same JWT claims, same refresh-token
		// rotation, same audit row.
		AuthResponse response = authService.issueSession(user, tenant);

		log.info("[google-auth] login OK -- tenant='{}', email='{}', publicUuid='{}'",
				tenant.getSlug(), user.getEmail(), user.getPublicUuid());
		auditLogger.log(AuditAction.LOGIN, "user",
				user.getPublicUuid(), "Google login OK (existing user)",
				Map.of(
						"tenantSlug", tenant.getSlug(),
						"email", user.getEmail(),
						"googleSubjectHash", Integer.toHexString(profile.subject().hashCode()),
						"remoteAddr", remoteAddr == null ? "" : remoteAddr
				));

		return response;
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