package com.edushift.modules.teachers.controller;

import com.edushift.modules.teachers.dto.CreateTeacherRequest;
import com.edushift.modules.teachers.dto.InviteTeacherResponse;
import com.edushift.modules.teachers.dto.LinkTeacherUserRequest;
import com.edushift.modules.teachers.dto.TeacherListItem;
import com.edushift.modules.teachers.dto.TeacherResponse;
import com.edushift.modules.teachers.dto.UpdateTeacherRequest;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.service.TeacherService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
 * REST adapter for the {@code teachers} module (Sprint 4 — BE-4.6).
 *
 * <h3>Endpoints (under {@code /api/v1/teachers})</h3>
 * <table>
 *   <caption>Teacher endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/                        </td><td>TENANT_ADMIN</td><td>{@link Page}&lt;{@link TeacherListItem}&gt;</td></tr>
 *   <tr><td>GET   </td><td>/{publicUuid}            </td><td>TENANT_ADMIN</td><td>{@link TeacherResponse}</td></tr>
 *   <tr><td>POST  </td><td>/                        </td><td>TENANT_ADMIN</td><td>{@link TeacherResponse} (201)</td></tr>
 *   <tr><td>PUT   </td><td>/{publicUuid}            </td><td>TENANT_ADMIN</td><td>{@link TeacherResponse}</td></tr>
 *   <tr><td>POST  </td><td>/{publicUuid}/link-user  </td><td>TENANT_ADMIN</td><td>{@link TeacherResponse}</td></tr>
 *   <tr><td>POST  </td><td>/{publicUuid}/invite     </td><td>TENANT_ADMIN</td><td>{@link InviteTeacherResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/{publicUuid}            </td><td>TENANT_ADMIN</td><td>204</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/teachers")
@Validated
@RequiredArgsConstructor
@Tag(name = "Teachers", description = "Teacher aggregate: CRUD + link-user + invite")
public class TeacherController {

	private final TeacherService service;

	@GetMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List teachers in the current tenant (TENANT_ADMIN)",
			description = "Paginated. Filters compose with AND: case-insensitive "
					+ "substring search across name/document/email; exact "
					+ "employmentStatus; hasUserAccount narrows by linkage."
	)
	public ResponseEntity<Page<TeacherListItem>> list(
			@Parameter(description = "Substring match on first/last/secondLastName, document, email")
			@RequestParam(required = false) String search,
			@Parameter(description = "Filter by exact employment status")
			@RequestParam(required = false) EmploymentStatus employmentStatus,
			@Parameter(description = "TRUE → only teachers linked to a user, FALSE → only unlinked")
			@RequestParam(required = false) Boolean hasUserAccount,
			@PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC)
			Pageable pageable
	) {
		return ResponseEntity.ok(
				service.listTeachers(search, employmentStatus, hasUserAccount, pageable));
	}

	@GetMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get a teacher by public UUID (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<TeacherResponse>> getOne(@PathVariable UUID publicUuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.getTeacher(publicUuid)));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create a teacher (TENANT_ADMIN)",
			description = "Returns 201 with the persisted projection. "
					+ "409 TEACHER_DOCUMENT_TAKEN on (documentType, documentNumber) collision; "
					+ "409 TEACHER_EMAIL_TAKEN on email collision."
	)
	public ResponseEntity<ApiResponse<TeacherResponse>> create(
			@Valid @RequestBody CreateTeacherRequest request
	) {
		TeacherResponse response = service.createTeacher(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PutMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Update a teacher (TENANT_ADMIN)",
			description = "Partial-merge: null fields are no-ops. Linking a User is "
					+ "NOT done here — see {@code POST /{publicUuid}/link-user}."
	)
	public ResponseEntity<ApiResponse<TeacherResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateTeacherRequest request
	) {
		TeacherResponse response = service.updateTeacher(publicUuid, request);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	@PostMapping("/{publicUuid}/link-user")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Link a teacher to an existing user (TENANT_ADMIN)",
			description = "User must be in the same tenant and have the TEACHER role. "
					+ "Errors: 409 TEACHER_ALREADY_HAS_USER, 409 USER_NOT_TEACHER_ROLE, "
					+ "409 USER_ALREADY_LINKED_TO_TEACHER, 404 RESOURCE_NOT_FOUND."
	)
	public ResponseEntity<ApiResponse<TeacherResponse>> linkUser(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody LinkTeacherUserRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.linkUser(publicUuid, request)));
	}

	@PostMapping("/{publicUuid}/invite")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Invite a teacher to claim a user account (TENANT_ADMIN)",
			description = "Creates a user_invitations row with role TEACHER and "
					+ "metadata.teacherId. Returns the invitation token + expiresAt. "
					+ "Errors: 409 TEACHER_ALREADY_HAS_USER, 422 TEACHER_NEEDS_EMAIL_TO_INVITE."
	)
	public ResponseEntity<ApiResponse<InviteTeacherResponse>> invite(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.invite(publicUuid)));
	}

	@DeleteMapping("/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-delete a teacher (TENANT_ADMIN)",
			description = "Soft delete: the row is marked deleted = true and "
					+ "drops out of every query. Refuses delete with "
					+ "409 TEACHER_HAS_ACTIVE_ASSIGNMENTS when the teacher "
					+ "has active assignments — soft-end them first."
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.deleteTeacher(publicUuid);
		return ResponseEntity.noContent().build();
	}
}
