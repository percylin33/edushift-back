package com.edushift.modules.disclosures.controller;

import com.edushift.modules.disclosures.dto.DisclosureCatalog;
import com.edushift.modules.disclosures.dto.DisclosureFieldDefinition;
import com.edushift.modules.disclosures.dto.DisclosurePackageResponse;
import com.edushift.modules.disclosures.dto.DisclosurePublicationResponse;
import com.edushift.modules.disclosures.dto.DisclosureVersionSummary;
import com.edushift.modules.disclosures.dto.ReviewNoteRequest;
import com.edushift.modules.disclosures.dto.SaveDisclosureRequest;
import com.edushift.modules.disclosures.dto.SaveDisclosureFieldRequest;
import com.edushift.modules.disclosures.service.AnnualDisclosureService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administration API for preparing and publishing annual school disclosures. */
@RestController
@RequestMapping("/disclosures")
@RequiredArgsConstructor
@Tag(name = "Annual disclosures", description = "Preparation, approval, publication and historical copies for private schools.")
@SecurityRequirement(name = "bearerAuth")
public class AnnualDisclosureController {
	private final AnnualDisclosureService service;

	@GetMapping("/catalog")
	@PreAuthorize("hasAuthority('LMS_DISCLOSURE_READ')")
	@Operation(summary = "Get the current disclosure checklist")
	public ApiResponse<List<DisclosureFieldDefinition>> catalog() {
		return ApiResponse.ok(DisclosureCatalog.fields());
	}

	@GetMapping("/years/{academicYearPublicUuid}")
	@PreAuthorize("hasAuthority('LMS_DISCLOSURE_READ')")
	@Operation(summary = "Get the draft workspace for an academic year")
	public ApiResponse<DisclosurePackageResponse> getWorkspace(@PathVariable UUID academicYearPublicUuid) {
		return ApiResponse.ok(service.getWorkspace(academicYearPublicUuid));
	}

	@PutMapping("/years/{academicYearPublicUuid}")
	@PreAuthorize("hasAuthority('LMS_DISCLOSURE_MANAGE') and !hasRole('TENANT_ADMIN')")
	@Operation(summary = "Save a draft or open the next revision after publication")
	public ApiResponse<DisclosurePackageResponse> save(
			@PathVariable UUID academicYearPublicUuid, @Valid @RequestBody SaveDisclosureRequest request) {
		return ApiResponse.ok(service.save(academicYearPublicUuid, request.content()));
	}

	@PutMapping("/years/{academicYearPublicUuid}/fields/{fieldKey}")
	@PreAuthorize("hasAuthority('LMS_DISCLOSURE_MANAGE') and !hasRole('TENANT_ADMIN')")
	@Operation(summary = "Save one field in the annual disclosure draft")
	@io.swagger.v3.oas.annotations.responses.ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Field saved"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid field or attachment data"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing disclosure permission"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Academic year not found"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Disclosure package is locked")
	})
	public ApiResponse<DisclosurePackageResponse> saveField(
			@PathVariable UUID academicYearPublicUuid, @PathVariable String fieldKey,
			@Valid @RequestBody SaveDisclosureFieldRequest request) {
		return ApiResponse.ok(service.saveField(academicYearPublicUuid, fieldKey,
				request.field(), request.attachments()));
	}

	@PostMapping("/years/{academicYearPublicUuid}/copy-previous")
	@PreAuthorize("hasAuthority('LMS_DISCLOSURE_MANAGE') and !hasRole('TENANT_ADMIN')")
	@Operation(summary = "Start a draft using the previous academic year's disclosure")
	public ApiResponse<DisclosurePackageResponse> copyPreviousYear(@PathVariable UUID academicYearPublicUuid) {
		return ApiResponse.ok(service.copyPreviousYear(academicYearPublicUuid));
	}

	@PostMapping("/years/{academicYearPublicUuid}/submit-review")
	@PreAuthorize("hasRole('STAFF') and hasAuthority('LMS_DISCLOSURE_SUBMIT') and !hasRole('TENANT_ADMIN')")
	@Operation(summary = "Submit the current draft for review")
	public ApiResponse<DisclosurePackageResponse> submitForReview(@PathVariable UUID academicYearPublicUuid) {
		return ApiResponse.ok(service.submitForReview(academicYearPublicUuid));
	}

	@PostMapping("/years/{academicYearPublicUuid}/request-changes")
	@PreAuthorize("hasAuthority('LMS_DISCLOSURE_REVIEW')")
	@Operation(summary = "Return a draft to editing with review notes")
	public ApiResponse<DisclosurePackageResponse> requestChanges(
			@PathVariable UUID academicYearPublicUuid, @Valid @RequestBody ReviewNoteRequest request) {
		return ApiResponse.ok(service.requestChanges(academicYearPublicUuid, request.note()));
	}

	@PostMapping("/years/{academicYearPublicUuid}/approve")
	@PreAuthorize("hasAuthority('LMS_DISCLOSURE_REVIEW')")
	@Operation(summary = "Approve an in-review draft")
	public ApiResponse<DisclosurePackageResponse> approve(@PathVariable UUID academicYearPublicUuid) {
		return ApiResponse.ok(service.approve(academicYearPublicUuid));
	}

	@PostMapping("/years/{academicYearPublicUuid}/publish")
	@PreAuthorize("hasAuthority('LMS_DISCLOSURE_PUBLISH')")
	@Operation(summary = "Publish the approved version to linked family accounts")
	public ApiResponse<DisclosurePackageResponse> publish(@PathVariable UUID academicYearPublicUuid) {
		return ApiResponse.ok(service.publish(academicYearPublicUuid));
	}

	@GetMapping("/years/{academicYearPublicUuid}/versions")
	@PreAuthorize("hasAuthority('LMS_DISCLOSURE_READ')")
	@Operation(summary = "List immutable publication versions")
	public ApiResponse<List<DisclosureVersionSummary>> versions(@PathVariable UUID academicYearPublicUuid) {
		return ApiResponse.ok(service.listVersions(academicYearPublicUuid));
	}

	@GetMapping("/years/{academicYearPublicUuid}/versions/{versionNumber}")
	@PreAuthorize("hasAuthority('LMS_DISCLOSURE_READ')")
	@Operation(summary = "Get a historical publication snapshot")
	public ApiResponse<DisclosurePublicationResponse> version(
			@PathVariable UUID academicYearPublicUuid, @PathVariable Integer versionNumber) {
		return ApiResponse.ok(service.getPublication(academicYearPublicUuid, versionNumber));
	}

	@GetMapping(value = "/years/{academicYearPublicUuid}/preview.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	@PreAuthorize("hasAuthority('LMS_DISCLOSURE_READ')")
	@Operation(summary = "Download a printable copy of the current workspace")
	public ResponseEntity<byte[]> preview(@PathVariable UUID academicYearPublicUuid) {
		return pdf(service.renderWorkspacePdf(academicYearPublicUuid), "expediente-anual.pdf");
	}

	private static ResponseEntity<byte[]> pdf(byte[] body, String filename) {
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.body(body);
	}
}
