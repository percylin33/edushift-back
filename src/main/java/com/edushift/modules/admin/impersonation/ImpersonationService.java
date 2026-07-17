package com.edushift.modules.admin.impersonation;

import com.edushift.infrastructure.ratelimit.SimpleRateLimiter;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.exception.TooManyRequestsException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sprint 15 / F-03 / H-05: impersonation guardrails.
 *
 * <ul>
 *   <li>Cannot impersonate yourself (F-03 + pre-existing).</li>
 *   <li>Cannot impersonate an inactive user (pre-existing).</li>
 *   <li>Cannot impersonate another SUPER_ADMIN (F-03 — new, blocks
 *       lateral movement across admin accounts).</li>
 *   <li>Per-admin rate limit of {@code IMPERSONATION_MAX_PER_HOUR}
 *       requests in a 1-hour rolling window (F-03 — new).</li>
 *   <li>Every accepted impersonation is logged with IP, path, method and
 *       body-hash metadata (F-03).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImpersonationService {

    /** Maximum impersonation accepts allowed per admin per rolling hour. */
    static final int IMPERSONATION_MAX_PER_HOUR = 30;
    /** Window for the impersonation rate limiter (ms). */
    static final int IMPERSONATION_WINDOW_MS = 60 * 60 * 1_000;

    private final UserRepository userRepository;
    private final AuditLogger auditLogger;
    private final SimpleRateLimiter rateLimiter;

    /**
     * Pre-flight guard: throws if the impersonation is rejected; returns
     * the resolved target otherwise. Always call this BEFORE swapping the
     * SecurityContext — the rate-limit consumes a token only on accept.
     */
    public User resolveTarget(UUID targetPublicUuid, UUID adminUuid) {
        User target = userRepository.findByPublicUuid(targetPublicUuid)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        if (target.getPublicUuid().equals(adminUuid)) {
            throw new BadRequestException("CANNOT_IMPERSONATE_SELF",
                    "Cannot impersonate yourself");
        }

        if (target.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("CANNOT_IMPERSONATE_INACTIVE",
                    "Cannot impersonate inactive user");
        }

        // F-03: lateral-movement guard — refuse to impersonate another
        // SUPER_ADMIN. Impersonation is for SUPPORT purposes only.
        if (target.hasRole(UserRole.SUPER_ADMIN)) {
            throw new ForbiddenException("CANNOT_IMPERSONATE_SUPER_ADMIN",
                    "Cannot impersonate another SUPER_ADMIN");
        }

        // F-03: per-admin throttling. Keyed by admin UUID so each
        // SUPER_ADMIN gets its own bucket.
        String bucketKey = "impersonation:" + adminUuid;
        if (!rateLimiter.allow(bucketKey, IMPERSONATION_MAX_PER_HOUR,
                IMPERSONATION_WINDOW_MS)) {
            throw new TooManyRequestsException("IMPERSONATION_RATE_LIMITED",
                    "Impersonation rate limit exceeded for this account; retry later");
        }

        return target;
    }

    public void logImpersonation(UUID adminUuid, UUID targetUuid, UUID targetTenantId,
                                  String action, String path, String method, String ip,
                                  String bodyHash) {
        auditLogger.log(AuditAction.IMPERSONATE, "user", targetUuid,
                "Impersonation: admin=" + adminUuid + " target=" + targetUuid
                        + " action=" + action + " path=" + path,
                java.util.Map.of(
                        "adminId", adminUuid.toString(),
                        "targetId", targetUuid.toString(),
                        "tenantId", targetTenantId != null ? targetTenantId.toString() : null,
                        "action", action,
                        "path", path,
                        "method", method,
                        "ip", ip != null ? ip : "unknown",
                        "bodySha256", bodyHash != null ? bodyHash : ""));
        log.info("[impersonation] admin={} impersonates user={} for {} {} (bodyHash={})",
                adminUuid, targetUuid, method, path,
                bodyHash != null ? bodyHash.substring(0, Math.min(16, bodyHash.length())) : "");
    }
}

