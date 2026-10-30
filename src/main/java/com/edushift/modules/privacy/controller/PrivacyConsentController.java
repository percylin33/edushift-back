package com.edushift.modules.privacy.controller;

import com.edushift.modules.privacy.dto.ConsentResponse;
import com.edushift.modules.privacy.dto.CreateConsentRequest;
import com.edushift.modules.privacy.service.PrivacyConsentService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/privacy/consents")
@Validated
@RequiredArgsConstructor
@Tag(name = "Privacy", description = "Decisiones de consentimiento asociadas a una finalidad y aviso específico")
public class PrivacyConsentController {

	private final PrivacyConsentService consentService;

	@PostMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('PARENT')")
	@Operation(summary = "Registrar la decisión del apoderado junto con la finalidad y el aviso mostrado")
	public ResponseEntity<ApiResponse<ConsentResponse>> recordConsent(
			@Valid @RequestBody CreateConsentRequest request,
			HttpServletRequest servletRequest
	) {
		String clientIp = extractClientIp(servletRequest);
		String userAgent = servletRequest.getHeader("User-Agent");

		ConsentResponse response = consentService.recordConsent(request, clientIp, userAgent);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	@GetMapping("/student/{studentPublicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('PARENT')")
	@Operation(summary = "Obtener el consentimiento registrado más reciente para un estudiante")
	public ResponseEntity<ApiResponse<ConsentResponse>> getConsentForStudent(@PathVariable UUID studentPublicUuid) {
		ConsentResponse response = consentService.getLatestConsent(studentPublicUuid);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	@PostMapping("/{consentPublicUuid}/revoke")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('PARENT')")
	@Operation(summary = "Revocar un consentimiento previamente otorgado por la cuenta familiar")
	public ResponseEntity<ApiResponse<ConsentResponse>> revokeConsent(@PathVariable UUID consentPublicUuid) {
		return ResponseEntity.ok(ApiResponse.ok(consentService.revokeConsent(consentPublicUuid)));
	}

	private String extractClientIp(HttpServletRequest request) {
		String xForwarded = request.getHeader("X-Forwarded-For");
		if (xForwarded != null && !xForwarded.isBlank()) {
			return xForwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
