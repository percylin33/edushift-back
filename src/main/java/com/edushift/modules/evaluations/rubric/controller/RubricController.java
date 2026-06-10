package com.edushift.modules.evaluations.rubric.controller;

import com.edushift.modules.evaluations.rubric.dto.CreateRubricRequest;
import com.edushift.modules.evaluations.rubric.dto.RubricFilters;
import com.edushift.modules.evaluations.rubric.dto.RubricListItem;
import com.edushift.modules.evaluations.rubric.dto.RubricResponse;
import com.edushift.modules.evaluations.rubric.dto.UpdateRubricRequest;
import com.edushift.modules.evaluations.rubric.service.RubricService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code Rubric} aggregate (Sprint 5B / BE-5B.2).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>Rubric endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/academic/rubrics</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@code List<}{@link RubricListItem}{@code >}</td></tr>
 *   <tr><td>GET   </td><td>/academic/rubrics/system</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@code List<}{@link RubricListItem}{@code >}</td></tr>
 *   <tr><td>GET   </td><td>/academic/rubrics/{publicUuid}</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link RubricResponse}</td></tr>
 *   <tr><td>POST  </td><td>/academic/rubrics</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link RubricResponse} (201)</td></tr>
 *   <tr><td>POST  </td><td>/academic/rubrics/{publicUuid}/fork</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link RubricResponse} (201)</td></tr>
 *   <tr><td>PATCH </td><td>/academic/rubrics/{publicUuid}</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link RubricResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/academic/rubrics/{publicUuid}</td>
 *       <td>TENANT_ADMIN, TEACHER</td>
 *       <td>204</td></tr>
 * </table>
 *
 * <p>The endpoints are flat under {@code /academic/rubrics} (no
 * nested parent) because rubrics are not anchored to a course /
 * section / assignment — they are a per-tenant library. Forking is a
 * dedicated {@code POST /{uuid}/fork} route (vs. a flag on create) to
 * keep the URL space explicit.</p>
 */
@RestController
@RequestMapping
@Validated
@RequiredArgsConstructor
@Tag(name = "Rubrics",
		description = "Per-tenant scoring templates for evaluation of "
				+ "kind=RUBRIC (Sprint 5B — BE-5B.2)")
public class RubricController {

	private final RubricService service;

	@GetMapping("/academic/rubrics")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(
			summary = "List rubrics visible to the tenant "
					+ "(TENANT_ADMIN, TEACHER)",
			description = "Returns system + tenant-owned rubrics by default. "
					+ "Use ?systemOnly=true|false to filter, ?active=false to "
					+ "include deactivated rubrics (admin only), ?q= for a "
					+ "case-insensitive name / description search."
	)
	public ResponseEntity<List<RubricListItem>> list(
			@Parameter(description = "true=only system; false=only tenant-owned; "
					+ "null=both (default)")
			@RequestParam(name = "systemOnly", required = false) Boolean systemOnly,
			@Parameter(description = "Filter by activation flag (skip-on-null)")
			@RequestParam(name = "isActive", required = false) Boolean isActive,
			@Parameter(description = "Case-insensitive search over name and description")
			@RequestParam(name = "q", required = false) String q
	) {
		RubricFilters filters = new RubricFilters(systemOnly, isActive, q);
		return ResponseEntity.ok(service.listRubrics(filters));
	}

	@GetMapping("/academic/rubrics/system")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(
			summary = "List system (MINEDU-seed) rubrics "
					+ "(TENANT_ADMIN, TEACHER)",
			description = "Triggers the on-demand materialisation of the MINEDU "
					+ "seed library on the first call per tenant. Idempotent."
	)
	public ResponseEntity<List<RubricListItem>> listSystem() {
		return ResponseEntity.ok(service.listSystemRubrics());
	}

	@GetMapping("/academic/rubrics/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Get a rubric (TENANT_ADMIN, TEACHER)")
	public ResponseEntity<ApiResponse<RubricResponse>> getOne(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getRubric(publicUuid)));
	}

	@PostMapping("/academic/rubrics")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(
			summary = "Create a tenant-owned rubric (TENANT_ADMIN, TEACHER)",
			description = "409 RUB_NAME_EXISTS on case-insensitive name collision. "
					+ "400 RUB_CRITERIA_WEIGHT_SUM / RUB_CRITERIA_COUNT / "
					+ "RUB_LEVELS_COUNT / RUB_LEVEL_CODE_DUPLICATE / "
					+ "RUB_LEVEL_UNKNOWN / RUB_CRITERION_KEY_DUPLICATE / "
					+ "RUB_DESCRIPTOR_DUPLICATE on shape violations."
	)
	public ResponseEntity<ApiResponse<RubricResponse>> create(
			@Valid @RequestBody CreateRubricRequest request
	) {
		RubricResponse response = service.createRubric(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PostMapping("/academic/rubrics/{publicUuid}/fork")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(
			summary = "Fork a system rubric into a tenant-owned copy "
					+ "(TENANT_ADMIN, TEACHER)",
			description = "Source must be a system rubric (is_system=true). "
					+ "400 RUB_CANNOT_FORK_NON_SYSTEM otherwise. The fork is "
					+ "fully editable; criteria/levels can be overridden via "
					+ "the request body (otherwise copied from the source). "
					+ "All body fields are optional on fork: empty body "
					+ "produces an exact copy with a '(fork)' suffix on the "
					+ "name. Bean Validation on this endpoint is intentionally "
					+ "disabled — the service runs the same shape validation "
					+ "({@code RubricValidationService.assertShapeValid}) on "
					+ "the merged criteria/levels, so a partial body cannot "
					+ "produce an invalid rubric."
	)
	public ResponseEntity<ApiResponse<RubricResponse>> fork(
			@PathVariable UUID publicUuid,
			@RequestBody(required = false) CreateRubricRequest request
	) {
		RubricResponse response = service.forkRubric(publicUuid, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PatchMapping("/academic/rubrics/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(
			summary = "Update a rubric (TENANT_ADMIN, TEACHER)",
			description = "Partial-merge. 403 RUB_SYSTEM_READ_ONLY on a system "
					+ "rubric — use POST /{uuid}/fork instead. Empty body is a "
					+ "no-op. Replaces criteria/levels in full if provided "
					+ "(service runs the full shape validation on the result)."
	)
	public ResponseEntity<ApiResponse<RubricResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateRubricRequest request
	) {
		return ResponseEntity.ok(
				ApiResponse.ok(service.updateRubric(publicUuid, request)));
	}

	@DeleteMapping("/academic/rubrics/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(
			summary = "Soft-delete a rubric (TENANT_ADMIN, TEACHER)",
			description = "403 RUB_SYSTEM_READ_ONLY on a system rubric. The "
					+ "DB's ON DELETE RESTRICT on parent_rubric_id also blocks "
					+ "physical cascades, but the friendly 403 makes the FE's "
					+ "life easier."
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.deleteRubric(publicUuid);
		return ResponseEntity.noContent().build();
	}
}
