package com.edushift.modules.students.controller;

import com.edushift.modules.students.dto.GuardianProfileResponse;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.service.StudentGuardianService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-scoped guardian lookups outside the student-nested link CRUD.
 */
@RestController
@RequestMapping("/guardians")
@Validated
@RequiredArgsConstructor
@Tag(name = "Guardians", description = "Guardian profile lookups (tenant-scoped)")
public class GuardianController {

	private final StudentGuardianService service;

	@GetMapping("/by-document")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Lookup guardian by document (TENANT_ADMIN)",
			description = "Returns the guardian profile for pre-filling the add-guardian "
					+ "form when the document already exists in the tenant. "
					+ "404 RESOURCE_NOT_FOUND when no row matches."
	)
	public ResponseEntity<ApiResponse<GuardianProfileResponse>> lookupByDocument(
			@RequestParam @NotNull DocumentType documentType,
			@RequestParam @NotBlank @Size(min = 4, max = 20) String documentNumber
	) {
		return ResponseEntity.ok(ApiResponse.ok(
				service.lookupByDocument(documentType, documentNumber)));
	}
}
