package com.edushift.modules.ai.controller;

import com.edushift.modules.ai.dto.AiPromptResponse;
import com.edushift.modules.ai.dto.SaveAiPromptRequest;
import com.edushift.modules.ai.service.PromptManagementService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/ai/prompts")
@RequiredArgsConstructor
@Tag(name = "AI prompt management",
     description = "Admin CRUD for AI prompt templates (Sprint 18 / BE-18.5)")
public class PromptManagementController {

    private final PromptManagementService service;

    @GetMapping("/template-keys")
    @PreAuthorize("hasAuthority('LMS_AI_GENERATE')")
    @Operation(summary = "List all prompt template keys (admin index)")
    public ResponseEntity<ApiResponse<List<String>>> listTemplateKeys() {
        return ResponseEntity.ok(ApiResponse.ok(service.listTemplateKeys()));
    }

    @GetMapping("/{templateKey}")
    @PreAuthorize("hasAuthority('LMS_AI_GENERATE')")
    @Operation(summary = "List all versions of a prompt template")
    public ResponseEntity<ApiResponse<List<AiPromptResponse>>> listVersions(
            @PathVariable String templateKey) {
        return ResponseEntity.ok(ApiResponse.ok(service.listVersions(templateKey)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LMS_AI_GENERATE')")
    @Operation(summary = "Create or update a prompt version")
    public ResponseEntity<ApiResponse<AiPromptResponse>> save(
            @Valid @RequestBody SaveAiPromptRequest request) {
        AiPromptResponse response = service.save(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/{templateKey}/{version}/activate")
    @PreAuthorize("hasAuthority('LMS_AI_GENERATE')")
    @Operation(summary = "Activate a specific prompt version")
    public ResponseEntity<ApiResponse<AiPromptResponse>> activate(
            @PathVariable String templateKey,
            @PathVariable String version) {
        AiPromptResponse response = service.activate(templateKey, version);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{templateKey}/{version}")
    @PreAuthorize("hasAuthority('LMS_AI_GENERATE')")
    @Operation(summary = "Soft-delete a prompt version (must not be active)")
    public ResponseEntity<Void> softDelete(
            @PathVariable String templateKey,
            @PathVariable String version) {
        service.softDelete(templateKey, version);
        return ResponseEntity.noContent().build();
    }
}
