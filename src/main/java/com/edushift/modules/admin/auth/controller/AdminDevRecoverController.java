package com.edushift.modules.admin.auth.controller;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.infrastructure.seed.DevDataInitializer;
import com.edushift.modules.admin.auth.dto.AdminDevRecoverRequest;
import com.edushift.modules.admin.auth.dto.AdminDevRecoverResponse;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.repository.RefreshTokenRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.multitenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 15 / dev-only break-glass recovery for the SUPER_ADMIN seed
 * (DEBT-BE-F-02). Replaces the message that previously pointed at a
 * non-existent {@code /admin/recover} endpoint with a real one,
 * gated to {@code dev} / {@code local} profiles.
 *
 * <p><strong>What it solves.</strong> When the SUPER_ADMIN seed runs with
 * {@code dev.seed.super-admin.password} <em>unset</em>, the
 * {@code DevDataInitializer} generates a random 24-char password and
 * stamps the row with the {@code SUPER_ADMIN_RESET_REQUIRED_v1}
 * sentinel hash. {@code AdminAuthService.login} rejects any credential
 * against that sentinel and returns 401 PASSWORD_RESET_REQUIRED, which
 * historically pointed the operator at {@code POST /admin/recover} — an
 * endpoint that never existed. This controller implements that endpoint.</p>
 *
 * <p><strong>Threat model.</strong> Same as {@code AdminDevMfaController}:
 * a single operator running a local dev process. The dev code is read
 * from {@code edushift.admin.dev-bypass.code} (env
 * {@code EDUSHIFT_DEV_MFA_BYPASS_CODE}, default {@code "dev-bypass"}) and
 * compared in constant time. The endpoint is profile-gated
 * ({@code @Profile({"dev","local"})}), so the bean is not registered in
 * prod builds and Spring returns 404 unconditionally.</p>
 *
 * <p><strong>Defense in depth.</strong> Even with a valid dev code, the
 * service refuses to rotate any user that is NOT currently flagged with
 * the sentinel hash. This prevents an operator from using the dev
 * endpoint to silently rotate a real password (e.g. the
 * {@code --dev.seed.super-admin.password} path produces a real BCrypt
 * hash and would not be touchable here).</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/dev")
@Profile({"dev", "local"})
@Validated
@Tag(name = "Admin Dev Tools",
        description = "Dev-only SUPER_ADMIN break-glass (DEBT-BE-F-02)")
@RequiredArgsConstructor
public class AdminDevRecoverController {

    /** Charset used to compare the X-Dev-Code header. */
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;
    private final PlatformTransactionManager txManager;

    @Value("${edushift.admin.dev-bypass.code:dev-bypass}")
    private String expectedDevCode;

    /** Source of entropy for the rotated password. */
    private final SecureRandom secureRandom = new SecureRandom();

    @PostMapping(
            value = "/recover-super-admin",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Recover a SUPER_ADMIN seeded with the sentinel hash (dev only)",
            description = """
                    Bean-level @Profile gate: this endpoint does NOT exist
                    in prod builds. Validates the X-Dev-Code header in
                    constant time, looks up the SUPER_ADMIN by email and
                    refuses any user whose password_hash is NOT the
                    sentinel prefix (defense in depth against reusing
                    the endpoint to rotate real BCrypt passwords).
                    Rotates the credential, persists a fresh BCrypt hash,
                    invalidates the existing refresh-token chain so the
                    previous session can't be replayed, and returns the
                    new plaintext password once — the controller is the
                    only place it ever appears in cleartext.
                    """)
    public ApiResponse<AdminDevRecoverResponse> recoverSuperAdmin(
            @RequestHeader(value = "X-Dev-Code", required = false) String devCode,
            @Valid @RequestBody AdminDevRecoverRequest request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);

        if (expectedDevCode == null || expectedDevCode.isBlank()) {
            log.warn("[admin-dev-recover] endpoint called but no dev code is "
                    + "configured — rejecting");
            throw new UnauthorizedException("DEV_CODE_NOT_CONFIGURED",
                    "Server-side dev-bypass code is not configured.");
        }
        if (devCode == null || !constantTimeEquals(devCode, expectedDevCode)) {
            log.warn("[admin-dev-recover] X-Dev-Code mismatch from ip={}", ip);
            auditLogger.log(AuditAction.ADMIN_PASSWORD_RESET_FAILED, "admin", null,
                    "X-Dev-Code mismatch on recover-super-admin from " + ip);
            throw new UnauthorizedException("DEV_CODE_MISMATCH",
                    "X-Dev-Code header is missing or does not match the server-side value.");
        }

        String normalizedEmail = request.email() == null
                ? "" : request.email().trim().toLowerCase();

        UUID sentinelId = TenantIdResolver.SUPER_ADMIN_SENTINEL;

        AdminDevRecoverResponse response = TenantContext.runAs(sentinelId, () ->
                new TransactionTemplate(txManager).execute(status -> {
                    User user = userRepository.findByEmail(normalizedEmail)
                            .orElseThrow(() -> {
                                log.warn("[admin-dev-recover] no such SUPER_ADMIN: email={}, ip={}",
                                        normalizedEmail, ip);
                                auditLogger.log(AuditAction.ADMIN_PASSWORD_RESET_FAILED,
                                        "admin", null,
                                        "No SUPER_ADMIN found for email=" + normalizedEmail
                                                + " from " + ip);
                                return new NotFoundException("SUPER_ADMIN_NOT_FOUND",
                                        "No SUPER_ADMIN with that email.");
                            });

                    // Defense in depth: only rotate users stamped with the sentinel hash.
                    // A real BCrypt hash (length ~60, prefix $2a$12$...) would not match
                    // this predicate, so the endpoint refuses to silently mutate it.
                    if (!DevDataInitializer.isSeedPasswordResetSentinel(user.getPasswordHash())) {
                        log.warn("[admin-dev-recover] user {} has a non-sentinel hash — refusing",
                                normalizedEmail);
                        auditLogger.log(AuditAction.ADMIN_PASSWORD_RESET_FAILED,
                                "admin", user.getPublicUuid(),
                                "Non-sentinel hash on recover-super-admin for " + normalizedEmail
                                        + " from " + ip);
                        throw new UnauthorizedException("NOT_A_SENTINEL_SEEDED_USER",
                                "This SUPER_ADMIN is not in the sentinel state and cannot be "
                                        + "recovered through this endpoint. Reset the password "
                                        + "directly via the database if needed.");
                    }

                    if (!user.hasRole(UserRole.SUPER_ADMIN)) {
                        log.warn("[admin-dev-recover] user {} is not SUPER_ADMIN — refusing",
                                normalizedEmail);
                        throw new UnauthorizedException("NOT_SUPER_ADMIN",
                                "Recovery endpoint only applies to SUPER_ADMIN users.");
                    }

                    String newPassword = generateRandomPassword();
                    String newHash = passwordEncoder.encode(newPassword);
                    Instant rotatedAt = Instant.now();

                    user.setPasswordHash(newHash);
                    user.setMfaEnabled(false); // force MFA enrolment on next login
                    userRepository.save(user);

                    // Invalidate the existing refresh-token chain so a leaked
                    // token from before the rotation can't be replayed.
                    int revoked = refreshTokenRepository.revokeAllByUser(user.getId(),
                            "super-admin dev-recover");

                    auditLogger.log(AuditAction.ADMIN_PASSWORD_RESET, "admin",
                            user.getPublicUuid(),
                            "SUPER_ADMIN password rotated via dev-recover; "
                                    + revoked + " refresh tokens revoked; ip=" + ip);

                    log.warn("====================================================================");
                    log.warn("[admin-dev-recover] NEW SUPER_ADMIN PASSWORD (shown once, "
                            + "NOT recoverable from the API):");
                    log.warn("[admin-dev-recover]   email:    {}", normalizedEmail);
                    log.warn("[admin-dev-recover]   password: {}", newPassword);
                    log.warn("[admin-dev-recover]   revoked:  {} refresh tokens", revoked);
                    log.warn("[admin-dev-recover] Next login will require MFA enrolment (H-02).");
                    log.warn("====================================================================");

                    return new AdminDevRecoverResponse(
                            normalizedEmail,
                            user.getPublicUuid(),
                            newPassword,
                            rotatedAt,
                            "Next login will require MFA enrolment (H-02). "
                                    + "Existing refresh tokens have been revoked.");
                }));

        return ApiResponse.ok(response);
    }

    /**
     * Same character set as {@code AdminDevMfaController.constantTimeEquals} —
     * kept private here so we don't expand the public API of the existing
     * controller. If a third dev-bypass endpoint is added, lift this helper
     * to a shared package-private utility.
     */
    private static boolean constantTimeEquals(String a, String b) {
        byte[] aa = a.getBytes(CHARSET);
        byte[] bb = b.getBytes(CHARSET);
        return MessageDigest.isEqual(aa, bb);
    }

    /**
     * Same charset / entropy source as {@code DevDataInitializer.generateRandomPassword}
     * — duplicated here rather than exposed to keep the seed module focused
     * on boot-time concerns (it does NOT depend on Spring Security beans).
     */
    private String generateRandomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$%^&*";
        StringBuilder sb = new StringBuilder(24);
        for (int i = 0; i < 24; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return xff.substring(0, comma).trim();
        }
        return request.getRemoteAddr();
    }
}
