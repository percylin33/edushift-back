package com.edushift.modules.sessions.template.controller;

import com.edushift.modules.sessions.template.dto.CreateSessionTemplateRequest;
import com.edushift.modules.sessions.template.dto.SessionTemplateResponse;
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
     description = "Reusable pedagogical templates (MINEDU archetypes + custom) — Sprint 18 / BE-18.3")
public class SessionTemplateController {

    private final SessionTemplateService service;

    @GetMapping("/session-templates")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TEACHER')")
    @Operation(summary = "List all session templates (system + tenant custom)")
    public ResponseEntity<ApiResponse<List<SessionTemplateResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.listAll()));
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
    @Operation(summary = "Create a custom session template (TENANT_ADMIN only)")
    public ResponseEntity<ApiResponse<SessionTemplateResponse>> create(
            @Valid @RequestBody CreateSessionTemplateRequest request) {
        SessionTemplateResponse response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response));
    }

    @PutMapping("/session-templates/{publicUuid}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Update a custom session template (TENANT_ADMIN only)")
    public ResponseEntity<ApiResponse<SessionTemplateResponse>> update(
            @PathVariable UUID publicUuid,
            @Valid @RequestBody UpdateSessionTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(publicUuid, request)));
    }

    @DeleteMapping("/session-templates/{publicUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Soft-delete a custom session template (TENANT_ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
        service.delete(publicUuid);
        return ResponseEntity.noContent().build();
    }
}
