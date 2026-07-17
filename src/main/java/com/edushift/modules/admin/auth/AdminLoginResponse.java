package com.edushift.modules.admin.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminLoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSec,
        AdminUserSummary user
) implements AdminAuthService.LoginOutcome {

    public static AdminLoginResponse bearer(String accessToken, String refreshToken,
                                             long expiresInSec, AdminUserSummary user) {
        return new AdminLoginResponse(accessToken, refreshToken, "Bearer", expiresInSec, user);
    }

    /**
     * Admin user summary returned alongside the access/refresh pair.
     *
     * <p>Mirrors {@code com.edushift.modules.auth.dto.UserSummary} so the
     * frontend's {@code AdminLoginResponse} model and the regular
     * {@code AuthResponse} payload can be fed through the same
     * {@code auth.setSession(...)} pipeline without losing authorization
     * data.</p>
     *
     * <ul>
     *   <li>{@code fullName} — pre-concatenated display name (first + last),
     *       matches the {@code user.fullName} computed by the regular
     *       login endpoint so the FE doesn't have to special-case admin.</li>
     *   <li>{@code roles} — flat string array of the user's role codes
     *       (e.g. {@code ["SUPER_ADMIN"]}). The FE's
     *       {@code roleGuard([UserRole.SuperAdmin])} reads this list to
     *       gate the admin console. Without it the admin login flow
     *       silently boots the SUPER_ADMIN into the regular workspace
     *       layout because {@code auth.hasRole()} would resolve against
     *       an empty list.</li>
     * </ul>
     */
    public record AdminUserSummary(
            String publicUuid,
            String email,
            String firstName,
            String lastName,
            String fullName,
            List<String> roles
    ) {}
}
