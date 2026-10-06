package com.edushift.modules.teachers.controller;

import com.edushift.modules.students.dto.BulkImportJobResponse;
import com.edushift.modules.students.dto.BulkImportPreviewResponse;
import com.edushift.modules.students.service.BulkImportService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
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
 * REST adapter for the teacher bulk-import flow (Sprint cierre-B / F7).
 */
@RestController
@RequestMapping("/teachers/bulk-import")
@RequiredArgsConstructor
@Tag(name = "Teachers Bulk Import",
		description = "Asynchronous bulk import of teachers from .xlsx (cierre-B / F7)")
public class TeacherBulkImportController {

	private final BulkImportService service;

	@GetMapping("/template")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Download the .xlsx template for teachers (TENANT_ADMIN)")
	public ResponseEntity<byte[]> downloadTemplate() {
		byte[] body = service.generateTeachersTemplate();
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"plantilla-docentes.xlsx\"")
				.contentLength(body.length)
				.body(body);
	}

	@PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Preview spreadsheet headers and suggested column mapping")
	public ResponseEntity<ApiResponse<BulkImportPreviewResponse>> preview(
			@RequestParam("file") MultipartFile file
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.previewTeachersImport(file)));
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Upload an .xlsx and enqueue a teacher import job (TENANT_ADMIN)",
			description = "Parses asynchronously. Optional columnMapping JSON "
					+ "(canonical → exact header). Response is 202; poll GET "
					+ "/v1/teachers/bulk-import/{id} for progress."
	)
	public ResponseEntity<ApiResponse<BulkImportJobResponse>> upload(
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "columnMapping", required = false) String columnMappingJson
	) {
		Map<String, String> columnMapping = service.parseColumnMappingJson(columnMappingJson);
		BulkImportJobResponse response = service.enqueueTeachersImport(file, columnMapping);
		return ResponseEntity.accepted().body(ApiResponse.ok(response));
	}

	@GetMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Look up a teacher import job's status (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<BulkImportJobResponse>> getJob(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getJob(publicUuid)));
	}
}
