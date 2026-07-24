package com.edushift.modules.notifications.controller;

import com.edushift.modules.notifications.dto.RegisterDeviceRequest;
import com.edushift.modules.notifications.service.DeviceTokenService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FCM device token registry (Sprint cierre-C / B8).
 *
 * <p>The FE registers the FCM token right after login + on each app
 * start (heartbeat). When the user logs out we {@code DELETE} the
 * token so the {@link com.edushift.modules.notifications.fcm.FcmSender}
 * never picks it up.</p>
 */
@RestController
@RequestMapping("/notifications/devices")
@PreAuthorize("isAuthenticated()")
@Tag(name = "FCM Device Tokens",
		description = "FCM push registration per authenticated user (Sprint cierre-C / B8)")
@RequiredArgsConstructor
public class DeviceTokenController {

	private final DeviceTokenService service;

	@PostMapping
	@Operation(summary = "Register an FCM device token for the current user")
	public ApiResponse<Void> register(@Valid @RequestBody RegisterDeviceRequest req) {
		service.register(req);
		return ApiResponse.ok(null);
	}

	@PutMapping("/heartbeat")
	@Operation(summary = "Heartbeat: keep-alive + revive a previously deactivated token (e.g. after re-install)")
	public ApiResponse<Void> heartbeat(@RequestBody java.util.Map<String, String> body) {
		String token = body.get("token");
		if (token == null || token.isBlank()) {
			throw new com.edushift.shared.exception.BadRequestException(
					"FCM_HEARTBEAT_NO_TOKEN", "Body must contain a 'token' field");
		}
		service.heartbeat(token);
		return ApiResponse.ok(null);
	}

	@DeleteMapping
	@Operation(summary = "Unregister the FCM token for the current user (e.g. on logout)")
	public ApiResponse<Void> unregister(@RequestBody java.util.Map<String, String> body) {
		String token = body.get("token");
		if (token == null || token.isBlank()) {
			throw new com.edushift.shared.exception.BadRequestException(
					"FCM_UNREGISTER_NO_TOKEN", "Body must contain a 'token' field");
		}
		service.unregister(token);
		return ApiResponse.ok(null);
	}
}