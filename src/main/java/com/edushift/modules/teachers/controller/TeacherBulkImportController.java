package com.edushift.modules.teachers.controller;

import com.edushift.modules.students.dto.BulkImportJobResponse;
import com.edushift.modules.students.service.BulkImportService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST adapter for the teacher bulk-import flow (Sprint cierre-B / F7).
 *
 * <h3>Endpoints (all under {@code /api/v1/teachers/bulk-import})</h3>
 * <table>
 *   <caption>Bulk-import endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>POST</td>
 *       <td>/                  </td>
 *       <td>TENANT_ADMIN</td>
 *       <td>{@link BulkImportJobResponse} (202)</td></tr>
 *   <tr><td>GET </td>
 *       <td>/{publicUuid}      </td>
 *       <td>TENANT_ADMIN</td>
 *       <td>{@link BulkImportJobResponse}</td></tr>
 * </table>
 *
 * <p>The upload endpoint returns {@code 202 Accepted} because parsing
 * happens asynchronously; the response payload carries the
 * {@code publicUuid} the UI uses to poll progress. The flow reuses
 * {@link BulkImportService#getJob(UUID)} so admins see students and
 * teachers bulk-import jobs in the same admin console.</p>
 */
@RestController
@RequestMapping("/teachers/bulk-import")
@RequiredArgsConstructor
@Tag(name = "Teachers Bulk Import",
		description = "Asynchronous bulk import of teachers from .xlsx (cierre-B / F7)")
public class TeacherBulkImportController {

	private final BulkImportService service;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Upload an .xlsx and enqueue a teacher import job (TENANT_ADMIN)",
			description = "Parses asynchronously. Response is 202 with the "
					+ "PENDING job; clients poll GET /v1/teachers/bulk-import/{id} "
					+ "for progress. Required columns: documentType, documentNumber, "
					+ "firstName, lastName. Optional: secondLastName, birthDate, gender, "
					+ "email, phone, title, specializations (comma-separated), "
					+ "hireDate, employmentStatus."
	)
	public ResponseEntity<ApiResponse<BulkImportJobResponse>> upload(
			@RequestParam("file") MultipartFile file
	) {
		BulkImportJobResponse response = service.enqueueTeachersImport(file);
		return ResponseEntity.accepted().body(ApiResponse.ok(response));
	}

	@GetMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Look up a teacher import job's status (TENANT_ADMIN)",
			description = "Returns the current state of the job: counters "
					+ "(processedRows, errorRows, totalRows), per-row errors, "
					+ "and the overall status. 404 RESOURCE_NOT_FOUND when "
					+ "the id is unknown or belongs to another tenant."
	)
	public ResponseEntity<ApiResponse<BulkImportJobResponse>> getJob(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getJob(publicUuid)));
	}
}