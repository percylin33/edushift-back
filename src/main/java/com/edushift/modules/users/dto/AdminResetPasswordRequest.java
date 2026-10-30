package com.edushift.modules.users.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body for {@code POST /v1/users/{publicUuid}/reset-password}.
 *
 * <p>When omitted (null body), the API defaults to {@link AdminPasswordResetDelivery#EMAIL}
 * for backward compatibility with the empty POST.
 */
public record AdminResetPasswordRequest(
		@NotNull AdminPasswordResetDelivery delivery
) {
}
