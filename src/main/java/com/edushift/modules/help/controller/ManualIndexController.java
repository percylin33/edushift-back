package com.edushift.modules.help.controller;

import com.edushift.modules.help.dto.ManualChapter;
import com.edushift.modules.help.dto.ManualIndexEntry;
import com.edushift.modules.help.service.ManualIndexService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint that serves the help system for EduShift.
 *
 * <p>Two surfaces:
 * <ul>
 *   <li>{@code GET /api/v1/help/manuals} → index of available manuals
 *       (one per role), consumed by the FE grid.</li>
 *   <li>{@code GET /api/v1/help/manuals/{role}/{file}} → raw markdown
 *       content of one chapter, consumed by the FE viewer.</li>
 * </ul>
 *
 * <p>Both endpoints are intentionally public (no authentication required)
 * so that an unauthenticated user landing on the login screen can still
 * preview which manuals are available and read the onboarding chapter.
 * See {@code SecurityConfig.PUBLIC_PATHS}.</p>
 */
@RestController
@RequestMapping("/help/manuals")
@Tag(name = "Help", description = "User manuals index and content (public)")
public class ManualIndexController {

    private final ManualIndexService service;

    public ManualIndexController(ManualIndexService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List the available user manuals")
    public ApiResponse<List<ManualIndexEntry>> list() {
        return ApiResponse.ok(service.getIndex());
    }

    /**
     * Returns one chapter of a role's manual as raw markdown text.
     *
     * <p>The {@code role} segment is the canonical role key (e.g.
     * {@code TENANT_ADMIN}). The {@code file} segment must be one of
     * {@code README.md}, {@code 01-onboarding-y-acceso.md},
     * {@code 02-flujos-esenciales.md}, {@code 03-autoevaluacion.md} —
     * any other value returns 404 to prevent path traversal.</p>
     *
     * @return 200 with the chapter envelope, or 404 if the role or file
     *         is unknown.
     */
    @GetMapping("/{role}/{file}")
    @Operation(summary = "Get one chapter of a role's manual as raw markdown")
    public ResponseEntity<?> getChapter(
            @Parameter(description = "Canonical role key", example = "TENANT_ADMIN")
            @PathVariable("role") String role,
            @Parameter(description = "Chapter filename", example = "01-onboarding-y-acceso.md")
            @PathVariable("file") String file) {

        return service.getChapter(role, file)
                .<ResponseEntity<?>>map(c -> ResponseEntity.ok(ApiResponse.ok(c)))
                .orElseGet(() -> ResponseEntity.status(404).body(
                        ApiResponse.error("MANUAL_CHAPTER_NOT_FOUND",
                                "Manual chapter not found: " + role + "/" + file)));
    }
}