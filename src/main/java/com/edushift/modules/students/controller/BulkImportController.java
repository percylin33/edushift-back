package com.edushift.modules.students.controller;

import com.edushift.modules.students.dto.BulkImportJobResponse;
import com.edushift.modules.students.service.BulkImportService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
 * REST adapter for the student bulk-import flow.
 *
 * <h3>Endpoints (all under {@code /api/v1/students/bulk-import})</h3>
 * <table>
 *   <caption>Bulk-import endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/template       </td><td>TENANT_ADMIN</td>
 *       <td>{@code .xlsx} download (binary)</td></tr>
 *   <tr><td>POST  </td><td>/               </td><td>TENANT_ADMIN</td>
 *       <td>{@link BulkImportJobResponse} (202)</td></tr>
 *   <tr><td>GET   </td><td>/{publicUuid}   </td><td>TENANT_ADMIN</td>
 *       <td>{@link BulkImportJobResponse}</td></tr>
 * </table>
 *
 * <p>The upload endpoint returns {@code 202 Accepted} because the
 * actual parsing happens asynchronously; the response payload carries
 * the {@code publicUuid} the UI uses to poll progress.
 */
@RestController
@RequestMapping("/students/bulk-import")
@RequiredArgsConstructor
@Tag(name = "Students Bulk Import",
		description = "Asynchronous bulk import of students from .xlsx")
public class BulkImportController {

	private final BulkImportService service;

	// ===========================================================================
	// Template download
	// ===========================================================================

	@GetMapping("/template")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Download the .xlsx template (TENANT_ADMIN)",
			description = "Returns a workbook with the canonical header row, "
					+ "a sample student row, and a Reference sheet listing "
					+ "the allowed enum values."
	)
	public ResponseEntity<byte[]> downloadTemplate() {
		byte[] body = service.generateStudentsTemplate();
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"students-template.xlsx\"")
				.contentLength(body.length)
				.body(body);
	}

	// ===========================================================================
	// Upload
	// ===========================================================================

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Upload an .xlsx and enqueue an import job (TENANT_ADMIN)",
			description = "Parses asynchronously. Response is 202 with the "
					+ "PENDING job; clients poll GET /v1/students/bulk-import/{id} "
					+ "for progress. 422 INVALID_FILE / FILE_TOO_LARGE / "
					+ "UNSUPPORTED_FILE_TYPE on envelope-level rejections."
	)
	public ResponseEntity<ApiResponse<BulkImportJobResponse>> upload(
			@RequestParam("file") MultipartFile file
	) {
		BulkImportJobResponse response = service.enqueueStudentsImport(file);
		return ResponseEntity.accepted().body(ApiResponse.ok(response));
	}

	// ===========================================================================
	// Status
	// ===========================================================================

	@GetMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Look up a job's status (TENANT_ADMIN)",
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
