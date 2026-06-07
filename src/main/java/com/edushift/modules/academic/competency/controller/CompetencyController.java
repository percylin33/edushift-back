package com.edushift.modules.academic.competency.controller;

import com.edushift.modules.academic.competency.dto.CompetencyListItem;
import com.edushift.modules.academic.competency.dto.CompetencyReorderRequest;
import com.edushift.modules.academic.competency.dto.CompetencyResponse;
import com.edushift.modules.academic.competency.dto.CreateCompetencyRequest;
import com.edushift.modules.academic.competency.dto.SeedCompetenciesResponse;
import com.edushift.modules.academic.competency.dto.UpdateCompetencyRequest;
import com.edushift.modules.academic.competency.service.CompetencyService;
import com.edushift.modules.academic.competency.service.CompetencySeedService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code Competency} aggregate (Sprint 5A — BE-5A.2).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>Competency endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/academic/courses/{c}/competencies            </td><td>TENANT_ADMIN</td><td>{@code List<}{@link CompetencyListItem}{@code >}</td></tr>
 *   <tr><td>POST  </td><td>/academic/courses/{c}/competencies            </td><td>TENANT_ADMIN</td><td>{@link CompetencyResponse} (201)</td></tr>
 *   <tr><td>PATCH </td><td>/academic/courses/{c}/competencies/reorder    </td><td>TENANT_ADMIN</td><td>{@code List<}{@link CompetencyResponse}{@code >}</td></tr>
 *   <tr><td>POST  </td><td>/academic/courses/{c}/competencies/seed-defaults</td><td>TENANT_ADMIN</td><td>{@link SeedCompetenciesResponse}</td></tr>
 *   <tr><td>GET   </td><td>/academic/competencies/{publicUuid}           </td><td>TENANT_ADMIN</td><td>{@link CompetencyResponse}</td></tr>
 *   <tr><td>PUT   </td><td>/academic/competencies/{publicUuid}           </td><td>TENANT_ADMIN</td><td>{@link CompetencyResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/academic/competencies/{publicUuid}           </td><td>TENANT_ADMIN</td><td>204</td></tr>
 * </table>
 */
@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "Academic — Competencies",
		description = "MINEDU-style competencies per course (Sprint 5A — BE-5A.2)")
public class CompetencyController {

	private final CompetencyService service;
	private final CompetencySeedService seedService;

	// =========================================================================
	// Course-scoped routes
	// =========================================================================

	@GetMapping("/academic/courses/{courseUuid}/competencies")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List competencies of a course (TENANT_ADMIN)",
			description = "Sorted by displayOrder asc. Optional ?isActive=true|false. "
					+ "404 RESOURCE_NOT_FOUND if course UUID is unknown / cross-tenant."
	)
	public ResponseEntity<List<CompetencyListItem>> list(
			@PathVariable UUID courseUuid,
			@Parameter(description = "Filter by activation flag")
			@RequestParam(name = "isActive", required = false) Boolean isActive
	) {
		return ResponseEntity.ok(service.listCompetencies(courseUuid, isActive));
	}

	@PostMapping("/academic/courses/{courseUuid}/competencies")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create a competency inside a course (TENANT_ADMIN)",
			description = "If displayOrder is omitted, the new competency is appended "
					+ "(max(displayOrder) + 1). 409 COMPETENCY_CODE_TAKEN on case-insensitive "
					+ "code collision inside the same course. 409 COMPETENCY_ORDER_TAKEN on "
					+ "concurrent ordinal collision."
	)
	public ResponseEntity<ApiResponse<CompetencyResponse>> create(
			@PathVariable UUID courseUuid,
			@Valid @RequestBody CreateCompetencyRequest request
	) {
		CompetencyResponse response = service.createCompetency(courseUuid, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PatchMapping("/academic/courses/{courseUuid}/competencies/reorder")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Reorder competencies of a course (TENANT_ADMIN)",
			description = "Two-pass write to avoid tripping the partial unique index. "
					+ "409 COMPETENCY_OUT_OF_COURSE if a payload UUID belongs to another "
					+ "course. 409 COMPETENCY_REORDER_INVALID on duplicate publicUuid or "
					+ "displayOrder. Returns the full competency list of the course in "
					+ "the new order."
	)
	public ResponseEntity<ApiResponse<List<CompetencyResponse>>> reorder(
			@PathVariable UUID courseUuid,
			@Valid @RequestBody CompetencyReorderRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(
				service.reorderCompetencies(courseUuid, request)));
	}

	@PostMapping("/academic/courses/{courseUuid}/competencies/seed-defaults")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Seed the MINEDU minimal catalog into this course (TENANT_ADMIN)",
			description = "Idempotent: returns seeded=false if the course already has "
					+ "at least one competency. Returns unsupportedCourseCode=true if "
					+ "Course.code is not recognised by CompetencyDefaults (currently MAT, "
					+ "COMU). 404 RESOURCE_NOT_FOUND if course UUID is unknown / cross-tenant."
	)
	public ResponseEntity<ApiResponse<SeedCompetenciesResponse>> seedDefaults(
			@PathVariable UUID courseUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(seedService.seedForCourse(courseUuid)));
	}

	// =========================================================================
	// Flat competency routes
	// =========================================================================

	@GetMapping("/academic/competencies/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get a competency with its capacities (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<CompetencyResponse>> getOne(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getCompetency(publicUuid)));
	}

	@PutMapping("/academic/competencies/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Update a competency (TENANT_ADMIN)",
			description = "Partial-merge. Use PATCH /academic/courses/{courseUuid}/competencies/reorder "
					+ "to change displayOrder. 409 COMPETENCY_CODE_TAKEN on case-insensitive "
					+ "collision."
	)
	public ResponseEntity<ApiResponse<CompetencyResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateCompetencyRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(
				service.updateCompetency(publicUuid, request)));
	}

	@DeleteMapping("/academic/competencies/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-delete a competency (TENANT_ADMIN)",
			description = "Cascades soft-delete to its capacities. 409 COMPETENCY_IN_USE_BY_SESSIONS "
					+ "if learning sessions reference the competency (BE-5A.4 wires this up). "
					+ "Prefer setting isActive=false to hide a competency from FE without "
					+ "losing history."
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.deleteCompetency(publicUuid);
		return ResponseEntity.noContent().build();
	}
}
