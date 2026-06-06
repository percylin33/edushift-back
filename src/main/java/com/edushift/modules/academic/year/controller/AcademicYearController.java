package com.edushift.modules.academic.year.controller;

import com.edushift.modules.academic.year.dto.AcademicYearListItem;
import com.edushift.modules.academic.year.dto.AcademicYearResponse;
import com.edushift.modules.academic.year.dto.CreateAcademicYearRequest;
import com.edushift.modules.academic.year.dto.UpdateAcademicYearRequest;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.service.AcademicYearService;
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
 * REST adapter for the {@code academic.year} sub-module (Sprint 4 — BE-4.1).
 *
 * <h3>Endpoints (under {@code /api/v1/academic/years})</h3>
 * <table>
 *   <caption>Academic year endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/                            </td><td>TENANT_ADMIN</td><td>{@code List<}{@link AcademicYearListItem}{@code >}</td></tr>
 *   <tr><td>GET   </td><td>/{publicUuid}                </td><td>TENANT_ADMIN</td><td>{@link AcademicYearResponse}</td></tr>
 *   <tr><td>POST  </td><td>/                            </td><td>TENANT_ADMIN</td><td>{@link AcademicYearResponse} (201)</td></tr>
 *   <tr><td>PUT   </td><td>/{publicUuid}                </td><td>TENANT_ADMIN</td><td>{@link AcademicYearResponse}</td></tr>
 *   <tr><td>POST  </td><td>/{publicUuid}/activate       </td><td>TENANT_ADMIN</td><td>{@link AcademicYearResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/{publicUuid}                </td><td>TENANT_ADMIN</td><td>204</td></tr>
 * </table>
 *
 * <p>The list endpoint deliberately returns a plain {@code List} (not a
 * paginated {@link org.springframework.data.domain.Page}) — academic years
 * are typically &lt; 20 per tenant for the foreseeable future, so paging
 * adds noise without value.</p>
 */
@RestController
@RequestMapping("/academic/years")
@Validated
@RequiredArgsConstructor
@Tag(name = "Academic — Years",
		description = "Academic year aggregate: CRUD + activate (Sprint 4 — BE-4.1)")
public class AcademicYearController {

	private final AcademicYearService service;

	@GetMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List academic years in the current tenant (TENANT_ADMIN)",
			description = "Sorted by status (ACTIVE first) then startDate desc. "
					+ "Optional ?status filter to slice by lifecycle stage."
	)
	public ResponseEntity<List<AcademicYearListItem>> list(
			@Parameter(description = "Filter by exact lifecycle status")
			@RequestParam(required = false) AcademicYearStatus status
	) {
		return ResponseEntity.ok(service.listYears(status));
	}

	@GetMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get an academic year by public UUID (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<AcademicYearResponse>> getOne(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getYear(publicUuid)));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create an academic year (TENANT_ADMIN)",
			description = "Always created with status PLANNING. "
					+ "409 ACADEMIC_YEAR_NAME_TAKEN when the name collides with "
					+ "an existing year in this tenant (case-insensitive). "
					+ "409 ACADEMIC_YEAR_INVALID_DATE_RANGE when startDate >= endDate."
	)
	public ResponseEntity<ApiResponse<AcademicYearResponse>> create(
			@Valid @RequestBody CreateAcademicYearRequest request
	) {
		AcademicYearResponse response = service.createYear(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PutMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Update an academic year (TENANT_ADMIN)",
			description = "Partial-merge semantics: null fields = no change. "
					+ "409 ACADEMIC_YEAR_LOCKED if the year is CLOSED. "
					+ "409 ACADEMIC_YEAR_NAME_TAKEN on case-insensitive name collision."
	)
	public ResponseEntity<ApiResponse<AcademicYearResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateAcademicYearRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.updateYear(publicUuid, request)));
	}

	@PostMapping("/{publicUuid}/activate")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Activate an academic year (TENANT_ADMIN)",
			description = "Transitions the target year from PLANNING to ACTIVE. "
					+ "If another year is currently ACTIVE in the same tenant, it "
					+ "is automatically transitioned to CLOSED in the same "
					+ "transaction. Idempotent on already-ACTIVE targets. "
					+ "409 ACADEMIC_YEAR_NOT_ACTIVATABLE on CLOSED targets."
	)
	public ResponseEntity<ApiResponse<AcademicYearResponse>> activate(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.activateYear(publicUuid)));
	}

	@DeleteMapping("/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-delete an academic year (TENANT_ADMIN)",
			description = "Soft delete: the row is marked deleted=true. "
					+ "Re-creating a year with the same name afterwards is allowed. "
					+ "409 ACADEMIC_YEAR_IN_USE when the year is still ACTIVE."
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.deleteYear(publicUuid);
		return ResponseEntity.noContent().build();
	}
}
