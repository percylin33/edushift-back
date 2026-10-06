package com.edushift.modules.sessions.template.controller;

import com.edushift.modules.sessions.template.dto.AdoptSessionTemplateRequest;
import com.edushift.modules.sessions.template.dto.CreateSessionTemplateRequest;
import com.edushift.modules.sessions.template.dto.SessionTemplateListItem;
import com.edushift.modules.sessions.template.dto.SessionTemplateResponse;
import com.edushift.modules.sessions.template.dto.SessionTemplatesOrganizedResponse;
import com.edushift.modules.sessions.template.dto.UpdateSessionTemplateRequest;
import com.edushift.modules.sessions.template.service.SessionTemplateService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Session Templates",
		description = "MINEDU catalog + tenant custom session templates (organized)")
public class SessionTemplateController {

	private final SessionTemplateService service;

	@GetMapping("/session-templates/catalog")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TEACHER')")
	@Operation(summary = "List system catalog templates (materializes seed on first call)")
	public ResponseEntity<ApiResponse<List<SessionTemplateListItem>>> catalog() {
		return ResponseEntity.ok(ApiResponse.ok(service.listCatalog()));
	}

	@GetMapping("/session-templates/organized")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TEACHER')")
	@Operation(summary = "Organized view: system catalog + tenant templates grouped by family")
	public ResponseEntity<ApiResponse<SessionTemplatesOrganizedResponse>> organized() {
		return ResponseEntity.ok(ApiResponse.ok(service.listOrganized()));
	}

	@GetMapping("/session-templates")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TEACHER')")
	@Operation(summary = "List tenant working templates (adopted + custom)")
	public ResponseEntity<ApiResponse<List<SessionTemplateResponse>>> list() {
		return ResponseEntity.ok(ApiResponse.ok(service.listMine()));
	}

	@GetMapping("/session-templates/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TEACHER')")
	@Operation(summary = "Get a session template by publicUuid")
	public ResponseEntity<ApiResponse<SessionTemplateResponse>> get(
			@PathVariable UUID publicUuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.get(publicUuid)));
	}

	@PostMapping("/session-templates")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Create a custom session template")
	public ResponseEntity<ApiResponse<SessionTemplateResponse>> create(
			@Valid @RequestBody CreateSessionTemplateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(service.create(request)));
	}

	@PostMapping("/session-templates/{publicUuid}/adopt")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Adopt (clone) a catalog template into the tenant")
	public ResponseEntity<ApiResponse<SessionTemplateResponse>> adopt(
			@PathVariable UUID publicUuid,
			@RequestBody(required = false) AdoptSessionTemplateRequest request) {
		AdoptSessionTemplateRequest body = request != null
				? request
				: new AdoptSessionTemplateRequest(false);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(service.adopt(publicUuid, body)));
	}

	@PutMapping("/session-templates/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Update a tenant session template")
	public ResponseEntity<ApiResponse<SessionTemplateResponse>> update(
			@PathVariable UUID publicUuid,
			@Valid @RequestBody UpdateSessionTemplateRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(service.update(publicUuid, request)));
	}

	@PutMapping("/session-templates/{publicUuid}/default")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Mark a tenant template as the school default")
	public ResponseEntity<ApiResponse<SessionTemplateResponse>> setDefault(
			@PathVariable UUID publicUuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.setDefault(publicUuid)));
	}

	@DeleteMapping("/session-templates/{publicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Soft-delete a tenant session template")
	public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
		service.delete(publicUuid);
		return ResponseEntity.noContent().build();
	}
}
