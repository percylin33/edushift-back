package com.edushift.modules.academic.levelgrade.controller;

import com.edushift.modules.academic.levelgrade.dto.AcademicLevelResponse;
import com.edushift.modules.academic.levelgrade.dto.CreateAcademicLevelRequest;
import com.edushift.modules.academic.levelgrade.dto.UpdateAcademicLevelRequest;
import com.edushift.modules.academic.levelgrade.service.AcademicLevelService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code AcademicLevel} aggregate (Sprint 4 — BE-4.2).
 *
 * <h3>Endpoints (under {@code /api/v1/academic/levels})</h3>
 * <table>
 *   <caption>Academic level endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/                            </td><td>TENANT_ADMIN</td>
 *       <td>{@code List<}{@link AcademicLevelResponse}{@code >} (with grades)</td></tr>
 *   <tr><td>GET   </td><td>/{publicUuid}                </td><td>TENANT_ADMIN</td>
 *       <td>{@link AcademicLevelResponse}</td></tr>
 *   <tr><td>POST  </td><td>/                            </td><td>TENANT_ADMIN</td>
 *       <td>{@link AcademicLevelResponse} (201)</td></tr>
 *   <tr><td>PUT   </td><td>/{publicUuid}                </td><td>TENANT_ADMIN</td>
 *       <td>{@link AcademicLevelResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/{publicUuid}                </td><td>TENANT_ADMIN</td>
 *       <td>204</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/academic/levels")
@Validated
@RequiredArgsConstructor
@Tag(name = "Academic — Levels",
		description = "Academic level catalog (per-tenant; seeded on signup with INICIAL/PRIMARIA/SECUNDARIA)")
public class AcademicLevelController {

	private final AcademicLevelService service;

	@GetMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List academic levels with their grades (TENANT_ADMIN)",
			description = "Sorted by ordinal asc. Each level embeds its grades, "
					+ "also sorted by ordinal asc."
	)
	public ResponseEntity<List<AcademicLevelResponse>> list() {
		return ResponseEntity.ok(service.listLevels());
	}

	@GetMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get an academic level with its grades (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<AcademicLevelResponse>> getOne(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getLevel(publicUuid)));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create an academic level (TENANT_ADMIN)",
			description = "409 LEVEL_CODE_TAKEN when the code (case-insensitive) "
					+ "collides with an existing level in this tenant."
	)
	public ResponseEntity<ApiResponse<AcademicLevelResponse>> create(
			@Valid @RequestBody CreateAcademicLevelRequest request
	) {
		AcademicLevelResponse response = service.createLevel(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PutMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Update an academic level (TENANT_ADMIN)",
			description = "Partial-merge. 409 LEVEL_CODE_TAKEN on case-insensitive collision."
	)
	public ResponseEntity<ApiResponse<AcademicLevelResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateAcademicLevelRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.updateLevel(publicUuid, request)));
	}

	@DeleteMapping("/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-delete an academic level (TENANT_ADMIN)",
			description = "409 LEVEL_HAS_GRADES when the level still has grades — "
					+ "remove them first. (BE-4.4 will add LEVEL_IN_USE_BY_COURSES.)"
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.deleteLevel(publicUuid);
		return ResponseEntity.noContent().build();
	}
}
