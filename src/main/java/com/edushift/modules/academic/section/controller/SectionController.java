package com.edushift.modules.academic.section.controller;

import com.edushift.modules.academic.section.dto.CreateSectionRequest;
import com.edushift.modules.academic.section.dto.SectionListItem;
import com.edushift.modules.academic.section.dto.SectionResponse;
import com.edushift.modules.academic.section.dto.UpdateSectionRequest;
import com.edushift.modules.academic.section.service.SectionService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code Section} aggregate (Sprint 4 — BE-4.3).
 *
 * <h3>Endpoints (under {@code /api/v1/academic/sections})</h3>
 * <table>
 *   <caption>Section endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/                                       </td>
 *       <td>TENANT_ADMIN, TEACHER</td><td>{@code List<}{@link SectionListItem}{@code >}</td></tr>
 *   <tr><td>GET   </td><td>/{publicUuid}                           </td>
 *       <td>TENANT_ADMIN, TEACHER</td><td>{@link SectionResponse}</td></tr>
 *   <tr><td>POST  </td><td>/                                       </td>
 *       <td>TENANT_ADMIN</td><td>{@link SectionResponse} (201)</td></tr>
 *   <tr><td>PUT   </td><td>/{publicUuid}                           </td>
 *       <td>TENANT_ADMIN</td><td>{@link SectionResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/{publicUuid}                           </td>
 *       <td>TENANT_ADMIN</td><td>204</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/academic/sections")
@Validated
@RequiredArgsConstructor
@Tag(name = "Academic — Sections",
		description = "Sections (year × grade × letter) per tenant (Sprint 4 — BE-4.3)")
public class SectionController {

	private final SectionService service;

	@GetMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(
			summary = "List sections",
			description = "Default scope: ACTIVE academic year. Optional filters: "
					+ "?academicYearId, ?gradeId, ?levelId. If both gradeId and "
					+ "levelId are supplied, gradeId takes precedence (stricter scope). "
					+ "Readable by TEACHER so the attendance manual-fallback picker "
					+ "can cascade Grade -> Section (BE-6.8)."
	)
	public ResponseEntity<List<SectionListItem>> list(
			@Parameter(description = "Filter by academic year publicUuid (default = ACTIVE year)")
			@RequestParam(name = "academicYearId", required = false) UUID academicYearPublicUuid,
			@Parameter(description = "Filter by grade publicUuid")
			@RequestParam(name = "gradeId", required = false) UUID gradePublicUuid,
			@Parameter(description = "Filter by level publicUuid (mutually exclusive with gradeId)")
			@RequestParam(name = "levelId", required = false) UUID levelPublicUuid
	) {
		return ResponseEntity.ok(service.listSections(
				academicYearPublicUuid, gradePublicUuid, levelPublicUuid));
	}

	@GetMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Get a section by public UUID")
	public ResponseEntity<ApiResponse<SectionResponse>> getOne(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getSection(publicUuid)));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create a section (TENANT_ADMIN)",
			description = "409 SECTION_NAME_TAKEN on case-insensitive collision in "
					+ "the same (year, grade). 409 ACADEMIC_YEAR_LOCKED if the parent "
					+ "year is CLOSED. 404 if year/grade publicUuid does not exist "
					+ "in the tenant."
	)
	public ResponseEntity<ApiResponse<SectionResponse>> create(
			@Valid @RequestBody CreateSectionRequest request
	) {
		SectionResponse response = service.createSection(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PutMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Update a section (TENANT_ADMIN)",
			description = "Partial-merge. 409 ACADEMIC_YEAR_LOCKED if the year is CLOSED. "
					+ "409 SECTION_NAME_TAKEN on case-insensitive name collision."
	)
	public ResponseEntity<ApiResponse<SectionResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateSectionRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(
				service.updateSection(publicUuid, request)));
	}

	@DeleteMapping("/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-delete a section (TENANT_ADMIN)",
			description = "409 ACADEMIC_YEAR_LOCKED if the year is CLOSED. "
					+ "BE-4.8 will add 409 SECTION_HAS_ENROLLMENTS when enrollments wire in."
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.deleteSection(publicUuid);
		return ResponseEntity.noContent().build();
	}
}
