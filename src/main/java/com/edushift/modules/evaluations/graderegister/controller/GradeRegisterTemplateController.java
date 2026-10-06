package com.edushift.modules.evaluations.graderegister.controller;

import com.edushift.modules.evaluations.graderegister.dto.AdoptGradeRegisterTemplateRequest;
import com.edushift.modules.evaluations.graderegister.dto.CreateGradeCategoryRequest;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateListItem;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateResponse;
import com.edushift.modules.evaluations.graderegister.dto.UpdateGradeCategoryRequest;
import com.edushift.modules.evaluations.graderegister.dto.UpdateGradeRegisterTemplateRequest;
import com.edushift.modules.evaluations.graderegister.service.GradeRegisterTemplateService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Academic — Grade Register Templates",
		description = "Catalog + tenant formal grade-register templates (F1)")
public class GradeRegisterTemplateController {

	private final GradeRegisterTemplateService service;

	@GetMapping("/academic/grade-register-templates/catalog")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "List system catalog templates (seeds on first call)")
	public ResponseEntity<ApiResponse<List<GradeRegisterTemplateListItem>>> catalog() {
		return ResponseEntity.ok(ApiResponse.ok(service.listCatalog()));
	}

	@GetMapping("/academic/grade-register-templates")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "List tenant working templates for an academic year")
	public ResponseEntity<ApiResponse<List<GradeRegisterTemplateListItem>>> listMine(
			@RequestParam UUID yearUuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.listMine(yearUuid)));
	}

	@GetMapping("/academic/grade-register-templates/{uuid}")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get template with categories")
	public ResponseEntity<ApiResponse<GradeRegisterTemplateResponse>> get(
			@PathVariable UUID uuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.get(uuid)));
	}

	@PostMapping("/academic/grade-register-templates/{uuid}/adopt")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Adopt (clone) a catalog template; set replaceActive to swap the year default")
	public ResponseEntity<ApiResponse<GradeRegisterTemplateResponse>> adopt(
			@PathVariable UUID uuid,
			@Valid @RequestBody AdoptGradeRegisterTemplateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(service.adopt(uuid, request)));
	}

	@PutMapping("/academic/grade-register-templates/{uuid}")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Update a tenant template (name / default)")
	public ResponseEntity<ApiResponse<GradeRegisterTemplateResponse>> update(
			@PathVariable UUID uuid,
			@Valid @RequestBody UpdateGradeRegisterTemplateRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(service.update(uuid, request)));
	}

	@DeleteMapping("/academic/grade-register-templates/{uuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Soft-delete a tenant template")
	public void delete(@PathVariable UUID uuid) {
		service.delete(uuid);
	}

	@PostMapping("/academic/grade-register-templates/{uuid}/categories")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Add a category to a tenant template")
	public ResponseEntity<ApiResponse<GradeRegisterTemplateResponse>> addCategory(
			@PathVariable UUID uuid,
			@Valid @RequestBody CreateGradeCategoryRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(service.addCategory(uuid, request)));
	}

	@PutMapping("/academic/grade-categories/{uuid}")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Update a grade category")
	public ResponseEntity<ApiResponse<GradeRegisterTemplateResponse>> updateCategory(
			@PathVariable UUID uuid,
			@Valid @RequestBody UpdateGradeCategoryRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(service.updateCategory(uuid, request)));
	}

	@DeleteMapping("/academic/grade-categories/{uuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Soft-delete a grade category")
	public void deleteCategory(@PathVariable UUID uuid) {
		service.deleteCategory(uuid);
	}
}
