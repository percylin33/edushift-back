package com.edushift.modules.academic.unit.controller;

import com.edushift.modules.academic.unit.dto.CreateUnitRequest;
import com.edushift.modules.academic.unit.dto.UnitListItem;
import com.edushift.modules.academic.unit.dto.UnitReorderRequest;
import com.edushift.modules.academic.unit.dto.UnitResponse;
import com.edushift.modules.academic.unit.dto.UpdateUnitRequest;
import com.edushift.modules.academic.unit.service.UnitService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code Unit} aggregate (Sprint 5A — BE-5A.1).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>Unit endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/academic/courses/{courseUuid}/units            </td>
 *       <td>TENANT_ADMIN</td><td>{@code List<}{@link UnitListItem}{@code >}</td></tr>
 *   <tr><td>POST  </td><td>/academic/courses/{courseUuid}/units            </td>
 *       <td>TENANT_ADMIN</td><td>{@link UnitResponse} (201)</td></tr>
 *   <tr><td>PATCH </td><td>/academic/courses/{courseUuid}/units/reorder    </td>
 *       <td>TENANT_ADMIN</td><td>{@code List<}{@link UnitResponse}{@code >}</td></tr>
 *   <tr><td>GET   </td><td>/academic/units/{publicUuid}                    </td>
 *       <td>TENANT_ADMIN</td><td>{@link UnitResponse}</td></tr>
 *   <tr><td>PUT   </td><td>/academic/units/{publicUuid}                    </td>
 *       <td>TENANT_ADMIN</td><td>{@link UnitResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/academic/units/{publicUuid}                    </td>
 *       <td>TENANT_ADMIN</td><td>204</td></tr>
 * </table>
 *
 * <p>The CRUD lives under two roots on purpose: the
 * "list / create / reorder" endpoints are scoped under their parent
 * course (so the FE never needs to load the course twice), while
 * "get / update / delete" use the flat {@code /academic/units/{uuid}}
 * shape because the FE already has the unit UUID in hand at that point.
 * Same pattern as {@code grades} (BE-4.2).</p>
 */
@RestController
@RequestMapping
@Validated
@RequiredArgsConstructor
@Tag(name = "Academic — Units",
		description = "Pedagogical units inside a course (Sprint 5A — BE-5A.1)")
public class UnitController {

	private final UnitService service;

	// =========================================================================
	// Course-scoped routes
	// =========================================================================

	@GetMapping("/academic/courses/{courseUuid}/units")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List units of a course (TENANT_ADMIN)",
			description = "Sorted by displayOrder asc. Optional ?isActive=true|false "
					+ "narrows by activation flag. 404 RESOURCE_NOT_FOUND if the course "
					+ "publicUuid is unknown for the tenant (incl. cross-tenant)."
	)
	public ResponseEntity<List<UnitListItem>> list(
			@PathVariable UUID courseUuid,
			@Parameter(description = "Filter by activation flag")
			@RequestParam(name = "isActive", required = false) Boolean isActive
	) {
		return ResponseEntity.ok(service.listUnits(courseUuid, isActive));
	}

	@PostMapping("/academic/courses/{courseUuid}/units")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create a unit inside a course (TENANT_ADMIN)",
			description = "If displayOrder is omitted, the new unit is appended "
					+ "(max(displayOrder) + 1). 409 UNIT_NAME_EXISTS on case-insensitive "
					+ "name collision inside the same course. 400 UNIT_DATE_INVERTED if "
					+ "endDate < startDate. 409 UNIT_ORDER_TAKEN on concurrent ordinal collision."
	)
	public ResponseEntity<ApiResponse<UnitResponse>> create(
			@PathVariable UUID courseUuid,
			@Valid @RequestBody CreateUnitRequest request
	) {
		UnitResponse response = service.createUnit(courseUuid, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PatchMapping("/academic/courses/{courseUuid}/units/reorder")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Reorder units of a course (TENANT_ADMIN)",
			description = "Two-pass write to avoid tripping the partial unique index. "
					+ "409 UNIT_OUT_OF_COURSE if a payload UUID belongs to another "
					+ "course (or does not exist). 409 UNIT_REORDER_INVALID on duplicate "
					+ "publicUuid or displayOrder inside the payload. Returns the full "
					+ "unit list of the course in the new order so the FE can re-render."
	)
	public ResponseEntity<ApiResponse<List<UnitResponse>>> reorder(
			@PathVariable UUID courseUuid,
			@Valid @RequestBody UnitReorderRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.reorderUnits(courseUuid, request)));
	}

	// =========================================================================
	// Flat unit routes
	// =========================================================================

	@GetMapping("/academic/units/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get a unit (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<UnitResponse>> getOne(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getUnit(publicUuid)));
	}

	@PutMapping("/academic/units/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Update a unit (TENANT_ADMIN)",
			description = "Partial-merge. Use PATCH /academic/courses/{courseUuid}/units/reorder "
					+ "to change displayOrder. 409 UNIT_NAME_EXISTS on case-insensitive "
					+ "name collision inside the same course. 400 UNIT_DATE_INVERTED if "
					+ "the post-merge state has endDate < startDate."
	)
	public ResponseEntity<ApiResponse<UnitResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateUnitRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.updateUnit(publicUuid, request)));
	}

	@DeleteMapping("/academic/units/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-delete a unit (TENANT_ADMIN)",
			description = "409 UNIT_HAS_SESSIONS if learning sessions still reference "
					+ "the unit (BE-5A.4 wires this up). Prefer setting isActive=false "
					+ "to hide a unit from FE without losing history."
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.deleteUnit(publicUuid);
		return ResponseEntity.noContent().build();
	}
}
