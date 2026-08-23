package com.edushift.modules.schedule.daytemplate.controller;

import com.edushift.modules.schedule.daytemplate.dto.CommitBootstrapRequest;
import com.edushift.modules.schedule.daytemplate.dto.DayTemplateResponse;
import com.edushift.modules.schedule.daytemplate.dto.ScheduleSourceDocumentResponse;
import com.edushift.modules.schedule.daytemplate.service.ScheduleBootstrapService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Schedule — Bootstrap",
		description = "Upload prior-year schedule files and commit draft jornada")
public class ScheduleBootstrapController {

	private final ScheduleBootstrapService service;

	@PostMapping(value = "/schedule/bootstrap/{yearUuid}/upload",
			consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Upload a prior-year schedule file (csv/xlsx/pdf/image)")
	public ResponseEntity<ApiResponse<ScheduleSourceDocumentResponse>> upload(
			@PathVariable UUID yearUuid,
			@RequestPart("file") MultipartFile file) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(service.upload(yearUuid, file)));
	}

	@GetMapping("/schedule/bootstrap/{yearUuid}")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "List bootstrap source documents for a year")
	public ResponseEntity<ApiResponse<List<ScheduleSourceDocumentResponse>>> list(
			@PathVariable UUID yearUuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.list(yearUuid)));
	}

	@PostMapping("/schedule/bootstrap/{yearUuid}/commit")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Commit a bootstrap document (seeds default templates in v1)")
	public ResponseEntity<ApiResponse<List<DayTemplateResponse>>> commit(
			@PathVariable UUID yearUuid,
			@RequestParam UUID documentUuid,
			@RequestBody(required = false) CommitBootstrapRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(
				service.commit(yearUuid, documentUuid, request)));
	}
}
