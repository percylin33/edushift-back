package com.edushift.modules.admin.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/admin/dev/recover-super-admin}.
 *
 * <p>Dev-only break-glass flow to recover a SUPER_ADMIN whose credential was
 * seeded with the {@code SUPER_ADMIN_RESET_REQUIRED_v1} sentinel hash
 * (the default when {@code dev.seed.super-admin.password} is unset and
 * a random password was generated at startup). The endpoint:</p>
 * <ol>
 *   <li>Validates the static {@code X-Dev-Code} header against the
 *       configured dev code (constant-time compare).</li>
 *   <li>Looks up the SUPER_ADMIN by email; refuses any user that is
 *       not currently flagged with the sentinel hash (defense in depth
 *       against reusing the endpoint on accounts with real BCrypt
 *       passwords).</li>
 *   <li>Generates a new random 24-char password, hashes it with BCrypt,
 *       and stamps the user row. The plaintext is returned ONCE in the
 *       response — the controller is the only place it ever appears.</li>
 *   <li>Invalidates the user's refresh-token chain (the existing
 *       refresh tokens still match the publicUuid, so a forced
 *       {@code invalidated_at} flag is the simplest cross-cutting
 *       logout).</li>
 * </ol>
 *
 * <p><strong>Profile gate.</strong> Same as {@code AdminDevMfaController}:
 * the bean is registered only when {@code dev} or {@code local} is the
 * active profile. In prod the path is unmapped and Spring returns 404.</p>
 *
 * <p><strong>Why a dedicated DTO.</strong> Keeping the request shape on a
 * record (vs. inline parameters) lets OpenAPI emit a clean schema and
 * lets Bean Validation enforce non-blank + email-shape on the email
 * field at the boundary.</p>
 *
 * @param email the SUPER_ADMIN email to recover (must match a row whose
 *              {@code password_hash} starts with the sentinel prefix)
 */
public record AdminDevRecoverRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email
) {

    @Override
    public String toString() {
        return "AdminDevRecoverRequest[email=" + email + "]";
    }
}
