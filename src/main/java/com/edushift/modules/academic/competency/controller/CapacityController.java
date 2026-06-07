package com.edushift.modules.academic.competency.controller;

import com.edushift.modules.academic.competency.dto.CapacityReorderRequest;
import com.edushift.modules.academic.competency.dto.CapacityResponse;
import com.edushift.modules.academic.competency.dto.CreateCapacityRequest;
import com.edushift.modules.academic.competency.dto.UpdateCapacityRequest;
import com.edushift.modules.academic.competency.service.CapacityService;
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
 * REST adapter for the {@code Capacity} aggregate (Sprint 5A — BE-5A.2).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>Capacity endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/academic/competencies/{c}/capacities          </td><td>TENANT_ADMIN</td><td>{@code List<}{@link CapacityResponse}{@code >}</td></tr>
 *   <tr><td>POST  </td><td>/academic/competencies/{c}/capacities          </td><td>TENANT_ADMIN</td><td>{@link CapacityResponse} (201)</td></tr>
 *   <tr><td>PATCH </td><td>/academic/competencies/{c}/capacities/reorder  </td><td>TENANT_ADMIN</td><td>{@code List<}{@link CapacityResponse}{@code >}</td></tr>
 *   <tr><td>GET   </td><td>/academic/capacities/{publicUuid}              </td><td>TENANT_ADMIN</td><td>{@link CapacityResponse}</td></tr>
 *   <tr><td>PUT   </td><td>/academic/capacities/{publicUuid}              </td><td>TENANT_ADMIN</td><td>{@link CapacityResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/academic/capacities/{publicUuid}              </td><td>TENANT_ADMIN</td><td>204</td></tr>
 * </table>
 */
@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "Academic — Capacities",
		description = "Capacities under a competency (Sprint 5A — BE-5A.2)")
public class CapacityController {

	private final CapacityService service;

	// =========================================================================
	// Competency-scoped routes
	// =========================================================================

	@GetMapping("/academic/competencies/{competencyUuid}/capacities")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List capacities of a competency (TENANT_ADMIN)",
			description = "Sorted by displayOrder asc. Optional ?isActive=true|false. "
					+ "404 RESOURCE_NOT_FOUND if competency UUID is unknown / cross-tenant."
	)
	public ResponseEntity<List<CapacityResponse>> list(
			@PathVariable UUID competencyUuid,
			@Parameter(description = "Filter by activation flag")
			@RequestParam(name = "isActive", required = false) Boolean isActive
	) {
		return ResponseEntity.ok(service.listCapacities(competencyUuid, isActive));
	}

	@PostMapping("/academic/competencies/{competencyUuid}/capacities")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create a capacity inside a competency (TENANT_ADMIN)",
			description = "If displayOrder is omitted, the new capacity is appended. "
					+ "409 CAPACITY_CODE_TAKEN on case-insensitive code collision. "
					+ "409 CAPACITY_ORDER_TAKEN on concurrent ordinal collision."
	)
	public ResponseEntity<ApiResponse<CapacityResponse>> create(
			@PathVariable UUID competencyUuid,
			@Valid @RequestBody CreateCapacityRequest request
	) {
		CapacityResponse response = service.createCapacity(competencyUuid, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PatchMapping("/academic/competencies/{competencyUuid}/capacities/reorder")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Reorder capacities of a competency (TENANT_ADMIN)",
			description = "Two-pass write. 409 CAPACITY_OUT_OF_COMPETENCY if a payload "
					+ "UUID belongs to another competency. 409 CAPACITY_REORDER_INVALID "
					+ "on duplicate publicUuid or displayOrder."
	)
	public ResponseEntity<ApiResponse<List<CapacityResponse>>> reorder(
			@PathVariable UUID competencyUuid,
			@Valid @RequestBody CapacityReorderRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(
				service.reorderCapacities(competencyUuid, request)));
	}

	// =========================================================================
	// Flat capacity routes
	// =========================================================================

	@GetMapping("/academic/capacities/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get a capacity (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<CapacityResponse>> getOne(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getCapacity(publicUuid)));
	}

	@PutMapping("/academic/capacities/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Update a capacity (TENANT_ADMIN)",
			description = "Partial-merge. Use PATCH /academic/competencies/{competencyUuid}/capacities/reorder "
					+ "to change displayOrder. 409 CAPACITY_CODE_TAKEN on case-insensitive "
					+ "collision."
	)
	public ResponseEntity<ApiResponse<CapacityResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateCapacityRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(
				service.updateCapacity(publicUuid, request)));
	}

	@DeleteMapping("/academic/capacities/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-delete a capacity (TENANT_ADMIN)",
			description = "409 CAPACITY_IN_USE_BY_SESSIONS if learning sessions reference "
					+ "the capacity (BE-5A.4 wires this up). Prefer setting isActive=false "
					+ "to hide a capacity from FE without losing history."
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.deleteCapacity(publicUuid);
		return ResponseEntity.noContent().build();
	}
}
