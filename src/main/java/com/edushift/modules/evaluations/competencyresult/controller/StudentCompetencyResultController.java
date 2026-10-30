package com.edushift.modules.evaluations.competencyresult.controller;

import com.edushift.modules.evaluations.competencyresult.dto.StudentCompetencyResultResponse;
import com.edushift.modules.evaluations.competencyresult.dto.StudentCompetencyResultRevisionResponse;
import com.edushift.modules.evaluations.competencyresult.dto.UpsertStudentCompetencyResultsRequest;
import com.edushift.modules.evaluations.competencyresult.service.StudentCompetencyResultService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/academic/evaluations/{evaluationUuid}/students/{studentUuid}/competency-results")
@Validated
@RequiredArgsConstructor
public class StudentCompetencyResultController {
	private final StudentCompetencyResultService service;

	@GetMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "List student competency results for an evaluation")
	public ResponseEntity<ApiResponse<List<StudentCompetencyResultResponse>>> list(
			@PathVariable UUID evaluationUuid, @PathVariable UUID studentUuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.list(evaluationUuid, studentUuid)));
	}

	@GetMapping("/{competencyUuid}/revisions")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Read immutable revisions of one student competency result")
	public ResponseEntity<ApiResponse<List<StudentCompetencyResultRevisionResponse>>> history(
			@PathVariable UUID evaluationUuid, @PathVariable UUID studentUuid,
			@PathVariable UUID competencyUuid) {
		return ResponseEntity.ok(ApiResponse.ok(
				service.history(evaluationUuid, studentUuid, competencyUuid)));
	}

	@PutMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Replace student competency results for an evaluation")
	public ResponseEntity<ApiResponse<List<StudentCompetencyResultResponse>>> replace(
			@PathVariable UUID evaluationUuid, @PathVariable UUID studentUuid,
			@Valid @RequestBody UpsertStudentCompetencyResultsRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(service.replace(evaluationUuid, studentUuid, request)));
	}
}
