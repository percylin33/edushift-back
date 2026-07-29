package com.edushift.modules.admin.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for {@code POST /api/v1/admin/dev/recover-super-admin}.
 *
 * <p>Carries enough information for the operator to immediately log in
 * with the recovered credential:</p>
 * <ul>
 *   <li>{@code newPassword} — plaintext shown once; never recoverable
 *       from the API again. Treat as break-glass material: paste into
 *       an authenticator, send through a secure channel, etc.</li>
 *   <li>{@code publicUuid} — confirms the target user (helpful when the
 *       operator has multiple SUPER_ADMINs seeded).</li>
 *   <li>{@code rotatedAt} — server-side timestamp, lets the operator
 *       correlate with the audit log row written by the service.</li>
 *   <li>{@code note} — short reminder to enrol MFA on the next login
 *       (the seed leaves {@code mfa_enabled=false} by design).</li>
 * </ul>
 */
public record AdminDevRecoverResponse(
        String email,
        UUID publicUuid,
        String newPassword,
        Instant rotatedAt,
        String note
) {}
