package com.edushift.modules.auth.controller;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEV-ONLY password reset endpoint.
 *
 * <p>Hit it from Postman to set any user's password to a known value when the
 * seed/sentinel reset never ran (i.e. BE started without {@code dev}/{@code local}
 * profile, or {@code DevDataInitializer} was bypassed).</p>
 *
 * <p><b>NEVER enable in production.</b> The class is gated by
 * {@code @Profile({"dev","local"})}; the route is also listed in
 * {@code SecurityConfig.PUBLIC_PATHS} for the same reason — it has no auth
 * requirement, so an attacker with network access could hijack any tenant's
 * admin.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 *   POST /api/v1/auth/_dev/reset-password
 *   Headers: X-Tenant-Slug: demo
 *   Body:    { "email": "admin@demo.edushift.pe", "newPassword": "Edushift123!" }
 * </pre>
 */
@RestController
@RequestMapping("/auth/_dev")
@Profile({"dev", "local"})
@Validated
@Tag(name = "Auth (dev)", description = "DEV-ONLY utilities. Gated by @Profile(dev|local).")
public class DevAuthController {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevAuthController(TenantRepository tenantRepository,
                             UserRepository userRepository,
                             PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 200) String newPassword
    ) {}

    @PostMapping("/reset-password")
    @Operation(summary = "DEV-ONLY: reset a user's password to a known value")
    @Transactional
    public ResponseEntity<ApiResponse<ResetPasswordResult>> resetPassword(
            @RequestHeader("X-Tenant-Slug") String tenantSlug,
            @RequestBody ResetPasswordRequest request) {

        // Tenant.id (PK interno) — es lo que referencia users.tenant_id (FK).
        // ANTES: getPublicUuid(), que rompía findByEmailAndTenantId (H11).
        UUID tenantInternalId = tenantRepository.findBySlugIgnoreCase(tenantSlug)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown tenant: " + tenantSlug))
                .getId();

        String normalizedEmail = request.email().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmailAndTenantId(normalizedEmail, tenantInternalId);

        User user = userOpt
                .orElseThrow(() -> new IllegalArgumentException(
                        "User '" + normalizedEmail + "' not found in tenant '" + tenantSlug + "'"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.ok(
                new ResetPasswordResult(user.getPublicUuid(), user.getEmail(),
                        tenantSlug, "Password reset OK. Login with the new value.")));
    }

    public record ResetPasswordResult(UUID publicUuid, String email, String tenantSlug, String message) {}
}
