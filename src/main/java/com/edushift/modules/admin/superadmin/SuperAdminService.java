package com.edushift.modules.admin.superadmin;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.modules.admin.superadmin.dto.CreateSuperAdminRequest;
import com.edushift.modules.admin.superadmin.dto.SuperAdminSummary;
import com.edushift.modules.admin.superadmin.mapper.SuperAdminMapper;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sprint 15 / F-06 / H-04: lifecycle for SUPER_ADMIN accounts.
 *
 * <p>Only SUPER_ADMINs may invoke this service (enforced by
 * {@code @PreAuthorize("hasRole('SUPER_ADMIN')")} on the controller).
 * Quorum: at least one OTHER active SUPER_ADMIN must remain after a
 * disable action; this prevents a single admin from locking the platform
 * out. Future work (H-04 follow-up) will add multi-actor quorum for
 * destructive ops (impersonation start, tenant suspension, payment
 * refund).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminService {

	/**
	 * Sentinel password hash for newly created SUPER_ADMINs. Mirrors the
	 * convention established by {@code DevDataInitializer}: a recognizable
	 * placeholder that {@code AdminAuthService.login} rejects with
	 * {@code PASSWORD_RESET_REQUIRED} so the operator must run the
	 * break-glass recovery flow before any privileged action.
	 */
	private static final String SUPER_ADMIN_RESET_SENTINEL_HASH =
			"SUPER_ADMIN_RESET_REQUIRED_v1_new_user";

	private final UserRepository userRepository;
	private final AuditLogger auditLogger;
	private final PlatformTransactionManager txManager;

	public List<SuperAdminSummary> list() {
		return TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL, () ->
				userRepository.findAll().stream()
						.filter(SuperAdminService::isSuperAdminActive)
						.map(SuperAdminMapper::toSummary)
						.toList());
	}

	public SuperAdminSummary create(CreateSuperAdminRequest request, UUID actorUuid) {
		String email = request.email().trim().toLowerCase();
		return TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL, () ->
				new TransactionTemplate(txManager).execute(status -> {
					if (userRepository.existsByEmail(email)) {
						throw new ConflictException("EMAIL_TAKEN",
								"A SUPER_ADMIN with that email already exists");
					}
					User u = new User();
					u.setEmail(email);
					u.setPasswordHash(SUPER_ADMIN_RESET_SENTINEL_HASH);
					u.setFirstName(request.firstName());
					u.setLastName(request.lastName());
					u.setStatus(UserStatus.ACTIVE);
					u.setEmailVerified(true);
					u.setMfaEnabled(false);
					u.addRole(UserRole.SUPER_ADMIN);
					User saved = userRepository.save(u);
					auditLogger.log(AuditAction.CREATE, "super_admin",
							saved.getPublicUuid(),
							"SUPER_ADMIN created",
							java.util.Map.of("email", email,
									"createdByActorUuid",
									actorUuid != null ? actorUuid.toString() : null));
					log.info("[super-admin] created new SUPER_ADMIN email='{}', publicUuid='{}'",
							email, saved.getPublicUuid());
					return SuperAdminMapper.toSummary(saved);
				}));
	}

	public SuperAdminSummary disable(UUID publicUuid, UUID actorUuid) {
		return TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL, () ->
				new TransactionTemplate(txManager).execute(status -> {
					User target = userRepository.findByPublicUuid(publicUuid)
							.orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
									"User not found"));
					if (!target.hasRole(UserRole.SUPER_ADMIN)) {
						throw new ForbiddenException("NOT_SUPER_ADMIN",
								"Target is not a SUPER_ADMIN");
					}
					if (actorUuid != null && target.getPublicUuid().equals(actorUuid)) {
						throw new BusinessException("SELF_DISABLE_FORBIDDEN",
								"SUPER_ADMIN cannot disable themselves; ask another admin");
					}
					if (target.getStatus() == UserStatus.INACTIVE) {
						throw new ConflictException("ALREADY_DISABLED",
								"Account is already disabled");
					}
					enforceQuorum(target.getPublicUuid());
					target.setStatus(UserStatus.INACTIVE);
					userRepository.saveAndFlush(target);
					auditLogger.log(AuditAction.UPDATE, "super_admin",
							target.getPublicUuid(),
							"SUPER_ADMIN disabled",
							java.util.Map.of("actorUuid",
									actorUuid != null ? actorUuid.toString() : null));
					return SuperAdminMapper.toSummary(target);
				}));
	}

	public long countActive() {
		return TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL, () ->
				userRepository.findAll().stream()
						.filter(SuperAdminService::isSuperAdminActive)
						.count());
	}

	/**
	 * Minimum quorum: at least one OTHER active SUPER_ADMIN must remain
	 * after this action. Prevents a single admin from locking the platform
	 * out.
	 */
	private void enforceQuorum(UUID excludingUuid) {
		long others = TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL,
				() -> userRepository.findAll().stream()
						.filter(SuperAdminService::isSuperAdminActive)
						.filter(u -> !u.getPublicUuid().equals(excludingUuid))
						.count());
		if (others == 0) {
			throw new ForbiddenException("QUORUM_REQUIRED",
					"At least one other active SUPER_ADMIN must remain");
		}
	}

	private static boolean isSuperAdminActive(User u) {
		return u != null
				&& u.hasRole(UserRole.SUPER_ADMIN)
				&& u.getStatus() == UserStatus.ACTIVE;
	}
}
