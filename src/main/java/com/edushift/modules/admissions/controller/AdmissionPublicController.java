package com.edushift.modules.admissions.controller;

import com.edushift.modules.admissions.dto.PublicAdmissionInquiryRequest;
import com.edushift.modules.admissions.dto.PublicAdmissionCatalogResponse;
import com.edushift.modules.admissions.dto.PublicInquiryReceipt;
import com.edushift.modules.admissions.dto.PublicOfferResponse;
import com.edushift.modules.admissions.service.AdmissionPublicFacade;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Narrow, tenant-slug-scoped public endpoints for family admission intake and offer response. */
@RestController
@RequestMapping("/public/admissions")
@RequiredArgsConstructor
@Tag(name = "Public Admissions", description = "Consultas familiares y confirmación segura de ofertas")
public class AdmissionPublicController {
	private final AdmissionPublicFacade publicFacade;

	@GetMapping("/{tenantSlug}/catalog")
	@Operation(summary = "Consultar años no cerrados y grados disponibles para enviar una consulta")
	public ResponseEntity<ApiResponse<PublicAdmissionCatalogResponse>> catalog(@PathVariable String tenantSlug) {
		return ResponseEntity.ok(ApiResponse.ok(publicFacade.catalog(tenantSlug)));
	}

	@PostMapping("/{tenantSlug}/inquiries")
	@Operation(summary = "Enviar una consulta inicial sin solicitar DNI")
	public ResponseEntity<ApiResponse<PublicInquiryReceipt>> submitInquiry(
			@PathVariable String tenantSlug,
			@Valid @RequestBody PublicAdmissionInquiryRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(publicFacade.submit(tenantSlug, request)));
	}

	@GetMapping("/{tenantSlug}/offers/{token}")
	@Operation(summary = "Consultar una oferta de vacante sin exponer datos personales")
	public ResponseEntity<ApiResponse<PublicOfferResponse>> getOffer(
			@PathVariable String tenantSlug, @PathVariable String token) {
		return ResponseEntity.ok(ApiResponse.ok(publicFacade.getOffer(tenantSlug, token)));
	}

	@PostMapping("/{tenantSlug}/offers/{token}/response")
	@Operation(summary = "Confirmar o rechazar una oferta usando su token privado")
	public ResponseEntity<ApiResponse<PublicOfferResponse>> respondToOffer(
			@PathVariable String tenantSlug,
			@PathVariable String token,
			@Valid @RequestBody RespondAdmissionOfferRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(publicFacade.respond(tenantSlug, token, request.accepted())));
	}

	public record RespondAdmissionOfferRequest(boolean accepted) {}
}
