package com.edushift.modules.evaluations.controller;

import com.edushift.modules.evaluations.dto.CreateEvaluationRequest;
import com.edushift.modules.evaluations.dto.EvaluationFilters;
import com.edushift.modules.evaluations.dto.EvaluationListItem;
import com.edushift.modules.evaluations.dto.EvaluationResponse;
import com.edushift.modules.evaluations.dto.UpdateEvaluationRequest;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.service.EvaluationService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
 * REST adapter for the {@code Evaluation} aggregate (Sprint 5B / BE-5B.1).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>Evaluation endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/academic/assignments/{uuid}/evaluations
 *       </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@code List<}{@link EvaluationListItem}{@code >}</td></tr>
 *   <tr><td>POST  </td><td>/academic/assignments/{uuid}/evaluations
 *       </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link EvaluationResponse} (201)</td></tr>
 *   <tr><td>GET   </td><td>/academic/evaluations/{publicUuid}
 *       </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link EvaluationResponse}</td></tr>
 *   <tr><td>PUT   </td><td>/academic/evaluations/{publicUuid}
 *       </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link EvaluationResponse}</td></tr>
 *   <tr><td>POST  </td><td>/academic/evaluations/{publicUuid}/publish
 *       </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link EvaluationResponse}</td></tr>
 *   <tr><td>POST  </td><td>/academic/evaluations/{publicUuid}/close
 *       </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>{@link EvaluationResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/academic/evaluations/{publicUuid}
 *       </td><td>TENANT_ADMIN, TEACHER</td>
 *       <td>204</td></tr>
 * </table>
 *
 * <p>CRUD lives under two roots on purpose, mirroring
 * {@code /academic/courses/{uuid}/units} (BE-5A.1) and
 * {@code /academic/assignments/{uuid}/learning-sessions} (BE-5A.4):
 * the listing and create paths are scoped under the parent assignment
 * so the FE never has to load the assignment twice, while
 * "get / update / delete / lifecycle" use the flat
 * {@code /academic/evaluations/{uuid}} shape because the FE already
 * has the evaluation UUID in hand at that point.</p>
 */
@RestController
@RequestMapping
@Validated
@RequiredArgsConstructor
@Tag(name = "Evaluations",
		description = "Graded items authored by a teacher against a "
				+ "TeacherAssignment (Sprint 5B — BE-5B.1)")
public class EvaluationController {

	private final EvaluationService service;

	// =========================================================================
	// Assignment-scoped routes
	// =========================================================================

	@GetMapping("/academic/assignments/{assignmentUuid}/evaluations")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(
			summary = "List evaluations of an assignment (TENANT_ADMIN, TEACHER)",
			description = "Ordered by scheduledDate desc. Optional filters: "
					+ "?status=DRAFT|PUBLISHED|CLOSED, ?isActive=true|false, "
					+ "?from=YYYY-MM-DD&to=YYYY-MM-DD. 404 RESOURCE_NOT_FOUND if the "
					+ "assignment publicUuid is unknown for the tenant."
	)
	public ResponseEntity<List<EvaluationListItem>> list(
			@PathVariable UUID assignmentUuid,
			@Parameter(description = "Filter by lifecycle status")
			@RequestParam(name = "status", required = false) EvaluationStatus status,
			@Parameter(description = "Filter by activation flag")
			@RequestParam(name = "isActive", required = false) Boolean isActive,
			@Parameter(description = "Inclusive lower bound on scheduledDate")
			@RequestParam(name = "from", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@Parameter(description = "Inclusive upper bound on scheduledDate")
			@RequestParam(name = "to", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		EvaluationFilters filters = new EvaluationFilters(status, isActive, from, to);
		return ResponseEntity.ok(service.listEvaluations(assignmentUuid, filters));
	}

	@PostMapping("/academic/assignments/{assignmentUuid}/evaluations")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(
			summary = "Create an evaluation against an assignment "
					+ "(TENANT_ADMIN, TEACHER)",
			description = "Initial status is always DRAFT. 409 EVAL_NAME_EXISTS on "
					+ "case-insensitive name collision. 400 EVAL_KIND_SCALE_MISMATCH on "
					+ "incoherent kind/scale. 400 EVAL_DATE_INVERTED if dueDate < scheduledDate. "
					+ "400 EVAL_UNIT_NOT_IN_COURSE / EVAL_SESSION_NOT_IN_ASSIGNMENT when the "
					+ "anchor FKs point outside the assignment's scope."
	)
	public ResponseEntity<ApiResponse<EvaluationResponse>> create(
			@PathVariable UUID assignmentUuid,
			@Valid @RequestBody CreateEvaluationRequest request
	) {
		EvaluationResponse response = service.createEvaluation(assignmentUuid, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	// =========================================================================
	// Flat evaluation routes
	// =========================================================================

	@GetMapping("/academic/evaluations/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Get an evaluation (TENANT_ADMIN, TEACHER)")
	public ResponseEntity<ApiResponse<EvaluationResponse>> getOne(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getEvaluation(publicUuid)));
	}

	@PutMapping("/academic/evaluations/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(
			summary = "Update an evaluation (TENANT_ADMIN, TEACHER)",
			description = "Partial-merge. Editability matrix: DRAFT = all fields; "
					+ "PUBLISHED = only description / dueDate / isActive; CLOSED = rejected "
					+ "(409 EVAL_CLOSED). 409 EVAL_NOT_EDITABLE on a PUBLISHED row when "
					+ "a frozen field is patched. 400 EVAL_KIND_SCALE_MISMATCH / "
					+ "EVAL_DATE_INVERTED are checked against the post-merge state."
	)
	public ResponseEntity<ApiResponse<EvaluationResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateEvaluationRequest request
	) {
		return ResponseEntity.ok(
				ApiResponse.ok(service.updateEvaluation(publicUuid, request)));
	}

	@PostMapping("/academic/evaluations/{publicUuid}/publish")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(
			summary = "Publish an evaluation (TENANT_ADMIN, TEACHER)",
			description = "Transitions DRAFT -> PUBLISHED. 409 EVAL_ILLEGAL_TRANSITION "
					+ "if the row is already PUBLISHED or CLOSED. Once PUBLISHED, only "
					+ "description / dueDate / isActive remain editable."
	)
	public ResponseEntity<ApiResponse<EvaluationResponse>> publish(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(
				ApiResponse.ok(service.publishEvaluation(publicUuid)));
	}

	@PostMapping("/academic/evaluations/{publicUuid}/close")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(
			summary = "Close an evaluation (TENANT_ADMIN, TEACHER)",
			description = "Transitions PUBLISHED -> CLOSED. 409 EVAL_ILLEGAL_TRANSITION "
					+ "if the row is still DRAFT (must publish first) or already CLOSED. "
					+ "CLOSED is terminal: the row becomes read-only."
	)
	public ResponseEntity<ApiResponse<EvaluationResponse>> close(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(
				ApiResponse.ok(service.closeEvaluation(publicUuid)));
	}

	@DeleteMapping("/academic/evaluations/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(
			summary = "Soft-delete an evaluation (TENANT_ADMIN, TEACHER)",
			description = "409 EVAL_HAS_GRADES if GradeRecord rows reference the "
					+ "evaluation (BE-5B.3 will light this guard up; for now deletes "
					+ "are always allowed). Prefer closing the evaluation instead of "
					+ "deleting it to preserve grade book history."
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.deleteEvaluation(publicUuid);
		return ResponseEntity.noContent().build();
	}
}
