package com.edushift.modules.admin.ai;

import com.edushift.modules.ai.rag.AiPlatformSourceResponse;
import com.edushift.modules.ai.rag.AiPlatformSourceReviewResponse;
import com.edushift.modules.ai.rag.AiPlatformSourceService;
import com.edushift.modules.ai.rag.PublishAiPlatformSourceRequest;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/ai/global-sources")
@RequiredArgsConstructor
@Tag(name = "Admin AI global sources", description = "EduShift-managed shared RAG corpus")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAiPlatformSourceController {

    private final AiPlatformSourceService service;

    @GetMapping
    @Operation(summary = "List EduShift global RAG sources")
    public ApiResponse<List<AiPlatformSourceResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{publicUuid}/review")
    @Operation(summary = "Get an extracted-text preview for a global source review")
    public ApiResponse<AiPlatformSourceReviewResponse> review(@PathVariable UUID publicUuid) {
        return ApiResponse.ok(service.review(publicUuid));
    }

    @GetMapping("/{publicUuid}/file")
    @Operation(summary = "Preview or download the original global source file")
    public ResponseEntity<InputStreamResource> file(@PathVariable UUID publicUuid) {
        var file = service.openFile(publicUuid);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(file.content()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a source to the shared EduShift RAG corpus")
    public ResponseEntity<ApiResponse<AiPlatformSourceResponse>> upload(
            @RequestParam String title,
            @RequestParam String sourceUrl,
            @RequestParam String sourceVersion,
            @RequestParam(required = false) LocalDate effectiveAt,
            @RequestParam LocalDate reviewAfter,
            @RequestParam Set<String> audiences,
            @RequestParam("file") MultipartFile file) {
        AiPlatformSourceResponse source = service.upload(title, sourceUrl, sourceVersion,
                effectiveAt, reviewAfter, audiences, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(source));
    }

    @PostMapping("/{publicUuid}/publish")
    @Operation(summary = "Publish a reviewed and indexed EduShift source")
    public ApiResponse<AiPlatformSourceResponse> publish(@PathVariable UUID publicUuid,
            @Valid @org.springframework.web.bind.annotation.RequestBody
            PublishAiPlatformSourceRequest review) {
        return ApiResponse.ok(service.publish(publicUuid, review));
    }

    @PostMapping("/{publicUuid}/retry")
    @Operation(summary = "Retry indexing a failed EduShift source")
    public ApiResponse<AiPlatformSourceResponse> retry(@PathVariable UUID publicUuid) {
        return ApiResponse.ok(service.retry(publicUuid));
    }

    @PostMapping("/{publicUuid}/reindex")
    @Operation(summary = "Reindex an existing EduShift source before reviewing it again")
    public ApiResponse<AiPlatformSourceResponse> reindex(@PathVariable UUID publicUuid) {
        return ApiResponse.ok(service.reindex(publicUuid));
    }

    @DeleteMapping("/{publicUuid}")
    @Operation(summary = "Retire a source from the shared EduShift corpus")
    public ResponseEntity<Void> retire(@PathVariable UUID publicUuid) {
        service.retire(publicUuid);
        return ResponseEntity.noContent().build();
    }
}
