package com.edushift.modules.ai.controller;

import com.edushift.modules.ai.rag.AiSourceRequest;
import com.edushift.modules.ai.rag.AiSourceResponse;
import com.edushift.modules.ai.rag.AiSourceService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/sources")
@RequiredArgsConstructor
@Tag(name = "AI sources", description = "Review and publish tenant-owned RAG documents")
public class AiSourceController {

    private final AiSourceService service;

    @GetMapping
    @PreAuthorize("hasAuthority('AI:SOURCE_MANAGE')")
    @Operation(summary = "List indexed sources of the current tenant")
    public ApiResponse<List<AiSourceResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('AI:SOURCE_MANAGE')")
    @Operation(summary = "Index a previously uploaded source file")
    public ResponseEntity<ApiResponse<AiSourceResponse>> register(
            @Valid @RequestBody AiSourceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.register(request)));
    }

    @PostMapping("/{publicUuid}/publish")
    @PreAuthorize("hasAuthority('AI:SOURCE_MANAGE')")
    @Operation(summary = "Publish a reviewed and indexed source")
    public ApiResponse<AiSourceResponse> publish(@PathVariable UUID publicUuid) {
        return ApiResponse.ok(service.publish(publicUuid));
    }

    @PostMapping("/{publicUuid}/retry")
    @PreAuthorize("hasAuthority('AI:SOURCE_MANAGE')")
    @Operation(summary = "Retry indexing a failed or textless source")
    public ApiResponse<AiSourceResponse> retry(@PathVariable UUID publicUuid) {
        return ApiResponse.ok(service.retry(publicUuid));
    }

    @DeleteMapping("/{publicUuid}")
    @PreAuthorize("hasAuthority('AI:SOURCE_MANAGE')")
    @Operation(summary = "Retire a source from retrieval immediately")
    public ResponseEntity<Void> retire(@PathVariable UUID publicUuid) {
        service.retire(publicUuid);
        return ResponseEntity.noContent().build();
    }
}
