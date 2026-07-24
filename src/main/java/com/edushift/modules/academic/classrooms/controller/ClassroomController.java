package com.edushift.modules.academic.classrooms.controller;

import com.edushift.modules.academic.classrooms.dto.ClassroomRequest;
import com.edushift.modules.academic.classrooms.dto.ClassroomResponse;
import com.edushift.modules.academic.classrooms.service.ClassroomService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the classroom catalog (Sprint cierre-C / B4).
 *
 * <h3>Endpoints (under {@code /api/v1/academic/classrooms})</h3>
 * <table>
 *   <caption>Classroom endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/                  </td><td>TENANT_ADMIN + STAFF</td>
 *       <td>Page of {@link ClassroomResponse}</td></tr>
 *   <tr><td>GET   </td><td>/{uuid}            </td><td>TENANT_ADMIN + STAFF</td>
 *       <td>{@link ClassroomResponse}</td></tr>
 *   <tr><td>POST  </td><td>/                  </td><td>TENANT_ADMIN</td>
 *       <td>{@link ClassroomResponse} (201)</td></tr>
 *   <tr><td>PUT   </td><td>/{uuid}            </td><td>TENANT_ADMIN</td>
 *       <td>{@link ClassroomResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/{uuid}            </td><td>TENANT_ADMIN</td>
 *       <td>204 (soft-delete)</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/academic/classrooms")
@RequiredArgsConstructor
@Tag(name = "Classrooms",
		description = "Tenant-defined physical classrooms catalog (Sprint cierre-C / B4)")
public class ClassroomController {

	private final ClassroomService service;

	@GetMapping
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','STAFF')")
	@Operation(summary = "List classrooms (paginated)")
	public ApiResponse<Page<ClassroomResponse>> list(Pageable pageable) {
		return ApiResponse.ok(service.list(pageable));
	}

	@GetMapping("/{publicUuid}")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','STAFF')")
	@Operation(summary = "Get a classroom")
	public ApiResponse<ClassroomResponse> get(@PathVariable UUID publicUuid) {
		return ApiResponse.ok(service.get(publicUuid));
	}

	@PostMapping
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Create a classroom (TENANT_ADMIN)")
	public ApiResponse<ClassroomResponse> create(@Valid @RequestBody ClassroomRequest req) {
		return ApiResponse.ok(service.create(req));
	}

	@PutMapping("/{publicUuid}")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Update a classroom (TENANT_ADMIN)")
	public ApiResponse<ClassroomResponse> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody ClassroomRequest req) {
		return ApiResponse.ok(service.update(publicUuid, req));
	}

	@DeleteMapping("/{publicUuid}")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Soft-delete a classroom (TENANT_ADMIN)")
	public ApiResponse<Void> delete(@PathVariable UUID publicUuid) {
		service.softDelete(publicUuid);
		return ApiResponse.ok(null);
	}
}