package com.edushift.modules.students.controller;

import com.edushift.modules.students.dto.CreateStudentRequest;
import com.edushift.modules.students.dto.StudentListFilters;
import com.edushift.modules.students.dto.StudentListItem;
import com.edushift.modules.students.dto.StudentResponse;
import com.edushift.modules.students.dto.UpdateStudentRequest;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.service.StudentService;
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
 * REST adapter for the {@code students} module.
 *
 * <h3>Endpoints (under {@code /api/v1/students})</h3>
 * <table>
 *   <caption>Student endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/                      </td><td>TENANT_ADMIN</td><td>{@link Page}&lt;{@link StudentListItem}&gt;</td></tr>
 *   <tr><td>GET   </td><td>/{publicUuid}          </td><td>TENANT_ADMIN</td><td>{@link StudentResponse}</td></tr>
 *   <tr><td>POST  </td><td>/                      </td><td>TENANT_ADMIN</td><td>{@link StudentResponse} (201)</td></tr>
 *   <tr><td>PUT   </td><td>/{publicUuid}          </td><td>TENANT_ADMIN</td><td>{@link StudentResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/{publicUuid}          </td><td>TENANT_ADMIN</td><td>204</td></tr>
 * </table>
 *
 * <p>{@code PUT} is used for partial updates instead of {@code PATCH} to
 * match the convention spelled out in the Sprint 3 plan ("Endpoints
 * estándar: POST, GET (list + filters), GET by uuid, PUT, DELETE"). The
 * service treats null fields as no-ops, so the semantics are still
 * partial-merge regardless of the HTTP verb.
 */
@RestController
@RequestMapping("/students")
@Validated
@RequiredArgsConstructor
@Tag(name = "Students", description = "Student aggregate: CRUD + filtered list")
public class StudentController {

	private final StudentService service;

	@GetMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List students in the current tenant (TENANT_ADMIN)",
			description = "Paginated list with optional filters: case-insensitive "
					+ "substring search across firstName/lastName/documentNumber, "
					+ "exact enrollmentStatus, gradeLevelId (forward-compat, "
					+ "ignored), currentSectionId and currentAcademicYearId "
					+ "(BE-4.8; restrict to students with an ACTIVE enrollment "
					+ "matching the given section / year)."
	)
	public ResponseEntity<Page<StudentListItem>> list(
			@Parameter(description = "Substring match on firstName/lastName/documentNumber")
			@RequestParam(required = false) String search,
			@Parameter(description = "Filter by exact enrollment status")
			@RequestParam(required = false) EnrollmentStatus enrollmentStatus,
			@Parameter(description = "Forward-compat: ignored")
			@RequestParam(required = false) String gradeLevelId,
			@Parameter(description = "Restrict to students with an ACTIVE enrollment in this section (publicUuid)")
			@RequestParam(required = false) UUID currentSectionId,
			@Parameter(description = "Restrict to students with an ACTIVE enrollment in this academic year (publicUuid)")
			@RequestParam(required = false) UUID currentAcademicYearId,
			@PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC)
			Pageable pageable
	) {
		StudentListFilters filters = new StudentListFilters(
				search, enrollmentStatus, gradeLevelId,
				currentSectionId, currentAcademicYearId);
		return ResponseEntity.ok(service.listStudents(filters, pageable));
	}

	@GetMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get a student by public UUID (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<StudentResponse>> getOne(@PathVariable UUID publicUuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.getStudent(publicUuid)));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create a student (TENANT_ADMIN)",
			description = "Returns 201 with the persisted projection. "
					+ "409 STUDENT_DOCUMENT_TAKEN when the (documentType, documentNumber) "
					+ "pair collides with an existing student in this tenant; "
					+ "409 STUDENT_EMAIL_TAKEN when the email collides."
	)
	public ResponseEntity<ApiResponse<StudentResponse>> create(
			@Valid @RequestBody CreateStudentRequest request
	) {
		StudentResponse response = service.createStudent(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PutMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Update a student (TENANT_ADMIN)",
			description = "Partial-merge semantics: null fields = no change, "
					+ "blank strings on nullable fields clear them. Same "
					+ "uniqueness conflicts as the create endpoint."
	)
	public ResponseEntity<ApiResponse<StudentResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateStudentRequest request
	) {
		StudentResponse response = service.updateStudent(publicUuid, request);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	@DeleteMapping("/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Soft-delete a student (TENANT_ADMIN)",
			description = "Soft delete: the row is marked deleted = true and "
					+ "drops out of every query. Re-creating a student with the "
					+ "same documentType/documentNumber afterwards is allowed."
	)
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.deleteStudent(publicUuid);
		return ResponseEntity.noContent().build();
	}
}
