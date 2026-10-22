package com.edushift.modules.claims.controller;

import com.edushift.modules.claims.dto.ClaimResponse;
import com.edushift.modules.claims.dto.CreateClaimRequest;
import com.edushift.modules.claims.service.ClaimsBookService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/claims")
@Validated
@RequiredArgsConstructor
@Tag(name = "Claims Public", description = "Libro de Reclamaciones Virtual Público — Ley N° 32495 (INDECOPI)")
public class PublicClaimsController {

	private final ClaimsBookService claimsService;

	@PostMapping
	@Operation(summary = "Registrar Reclamo o Queja en el Libro de Reclamaciones Virtual (Público, sin login)")
	public ResponseEntity<ApiResponse<ClaimResponse>> createClaim(@Valid @RequestBody CreateClaimRequest request) {
		ClaimResponse response = claimsService.createPublicClaim(request);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

}
