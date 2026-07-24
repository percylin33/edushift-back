package com.edushift.modules.notifications.dto;

import com.edushift.modules.notifications.entity.UserDeviceToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload of {@code POST /v1/notifications/devices} (Sprint cierre-C / B8).
 *
 * <p>The FE registers the FCM token after each successful login + on
 * app start if the token hasn't changed (heartbeat). The BE upserts
 * on {@code token} so a token rotation reassigns the user without
 * crashing.</p>
 */
public record RegisterDeviceRequest(
		@NotBlank @Size(min = 16, max = 512) String token,
		@NotNull UserDeviceToken.Platform platform
) {
}