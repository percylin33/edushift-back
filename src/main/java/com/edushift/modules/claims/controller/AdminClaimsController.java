package com.edushift.modules.claims.controller;

import com.edushift.modules.claims.dto.ClaimResponse;
import com.edushift.modules.claims.dto.RespondClaimRequest;
import com.edushift.modules.claims.entity.ClaimsBookEntry.ClaimStatus;
import com.edushift.modules.claims.service.ClaimsBookService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/claims")
@Validated
@RequiredArgsConstructor
@Tag(name = "Claims Admin", description = "Gestión administrativa y respuesta legal a reclamos INDECOPI")
public class AdminClaimsController {

	private final ClaimsBookService claimsService;

	@GetMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('SUPER_ADMIN')")
	@Operation(summary = "Listar reclamos registrados para atención dentro del plazo legal de 15 días")
	public ResponseEntity<Page<ClaimResponse>> listClaims(
			@RequestParam(required = false) ClaimStatus status,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
	) {
		Page<ClaimResponse> page = claimsService.listClaims(status, pageable);
		return ResponseEntity.ok(page);
	}

	@PostMapping("/{id}/respond")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('SUPER_ADMIN')")
	@Operation(summary = "Responder formalmente a un reclamo o queja")
	public ResponseEntity<ApiResponse<ClaimResponse>> respondClaim(
			@PathVariable UUID id,
			@Valid @RequestBody RespondClaimRequest request
	) {
		ClaimResponse response = claimsService.respondClaim(id, request);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}
}
