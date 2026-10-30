package com.edushift.modules.disclosures.controller;

import com.edushift.modules.disclosures.dto.DisclosurePublicationResponse;
import com.edushift.modules.disclosures.dto.DisclosureCatalog;
import com.edushift.modules.disclosures.dto.DisclosureFieldDefinition;
import com.edushift.modules.disclosures.service.AnnualDisclosureService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Published information for family users with an active student relationship. */
@RestController
@RequestMapping("/family/disclosures")
@RequiredArgsConstructor
@Tag(name = "Family disclosures", description = "Published annual school information and downloadable copies.")
@SecurityRequirement(name = "bearerAuth")
public class FamilyDisclosureController {
	private final AnnualDisclosureService service;
	private final CurrentUserProvider currentUserProvider;

	@GetMapping("/catalog")
	@PreAuthorize("hasAuthority('LMS_FAMILY_READ')")
	@Operation(summary = "Get labels for the published disclosure checklist")
	public ApiResponse<List<DisclosureFieldDefinition>> catalog() {
		return ApiResponse.ok(DisclosureCatalog.fields());
	}

	@GetMapping
	@PreAuthorize("hasAuthority('LMS_FAMILY_READ')")
	@Operation(summary = "List published annual disclosures for the current school")
	public ApiResponse<List<DisclosurePublicationResponse>> list() {
		return ApiResponse.ok(service.listFamilyPublications(currentUserId()));
	}

	@GetMapping("/{academicYearPublicUuid}")
	@PreAuthorize("hasAuthority('LMS_FAMILY_READ')")
	@Operation(summary = "Get the latest published disclosure for an academic year")
	public ApiResponse<DisclosurePublicationResponse> get(@PathVariable UUID academicYearPublicUuid) {
		return ApiResponse.ok(service.getFamilyPublication(academicYearPublicUuid, currentUserId()));
	}

	@GetMapping(value = "/{academicYearPublicUuid}/download", produces = MediaType.APPLICATION_PDF_VALUE)
	@PreAuthorize("hasAuthority('LMS_FAMILY_READ')")
	@Operation(summary = "Download an auditable printable copy of a published disclosure")
	public ResponseEntity<byte[]> download(@PathVariable UUID academicYearPublicUuid) {
		byte[] body = service.renderFamilyPdf(academicYearPublicUuid, currentUserId());
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"informacion-anual.pdf\"")
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(body);
	}

	private UUID currentUserId() {
		return currentUserProvider.currentUserId()
				.orElseThrow(() -> new com.edushift.shared.exception.ForbiddenException(
						"AUTHENTICATED_USER_REQUIRED", "Se requiere una cuenta familiar autenticada."));
	}
}
