package com.edushift.modules.students.controller;

import com.edushift.modules.students.dto.BulkImportJobResponse;
import com.edushift.modules.students.dto.BulkImportPreviewResponse;
import com.edushift.modules.students.service.BulkImportService;
import com.edushift.modules.students.service.bulk.StudentBulkImportOptions;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
 */
@RestController
@RequestMapping("/students/bulk-import")
@RequiredArgsConstructor
@Tag(name = "Students Bulk Import",
		description = "Asynchronous bulk import of students from .xlsx")
public class BulkImportController {

	private final BulkImportService service;

	@GetMapping("/template")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Download the .xlsx template (TENANT_ADMIN)")
	public ResponseEntity<byte[]> downloadTemplate() {
		byte[] body = service.generateStudentsTemplate();
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"plantilla-estudiantes.xlsx\"")
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
		return ResponseEntity.ok(ApiResponse.ok(service.previewStudentsImport(file)));
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Upload an .xlsx and enqueue an import job (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<BulkImportJobResponse>> upload(
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "columnMapping", required = false) String columnMappingJson,
			@RequestParam(value = "sectionPublicUuid", required = false) UUID sectionPublicUuid,
			@RequestParam(value = "academicYearPublicUuid", required = false)
			UUID academicYearPublicUuid,
			@RequestParam(value = "enrolledAt", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate enrolledAt
	) {
		Map<String, String> columnMapping = service.parseColumnMappingJson(columnMappingJson);
		StudentBulkImportOptions options = new StudentBulkImportOptions(
				columnMapping, sectionPublicUuid, academicYearPublicUuid, enrolledAt);
		BulkImportJobResponse response = service.enqueueStudentsImport(file, options);
		return ResponseEntity.accepted().body(ApiResponse.ok(response));
	}

	@GetMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Look up a job's status (TENANT_ADMIN)")
	public ResponseEntity<ApiResponse<BulkImportJobResponse>> getJob(
			@PathVariable UUID publicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.getJob(publicUuid)));
	}
}
