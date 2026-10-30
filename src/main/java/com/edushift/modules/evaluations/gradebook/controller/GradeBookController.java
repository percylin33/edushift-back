package com.edushift.modules.evaluations.gradebook.controller;

import com.edushift.modules.evaluations.gradebook.dto.GradeBookResponse;
import com.edushift.modules.evaluations.gradebook.service.GradeBookService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@Validated
@RequiredArgsConstructor
@Tag(name = "Grade Book",
        description = "On-the-fly Student × Evaluation matrix per TeacherAssignment")
public class GradeBookController {

    private final GradeBookService service;

    @GetMapping("/academic/teacher-assignments/{publicUuid}/gradebook")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
    @Operation(summary = "Get the grade book for a teacher assignment")
    public ResponseEntity<ApiResponse<GradeBookResponse>> getGradeBook(
            @PathVariable UUID publicUuid,
            @RequestParam(defaultValue = "false") boolean includeWithdrawn,
            @RequestParam(defaultValue = "false") boolean includeTransferred
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.buildGradeBook(publicUuid, includeWithdrawn, includeTransferred)));
    }

    @GetMapping("/academic/teacher-assignments/{publicUuid}/gradebook/export.xlsx")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
    @Operation(summary = "Export grade book as Excel")
    public ResponseEntity<byte[]> exportXlsx(
            @PathVariable UUID publicUuid,
            @RequestParam(defaultValue = "false") boolean includeWithdrawn,
            @RequestParam(defaultValue = "false") boolean includeTransferred
    ) {
        byte[] body = service.exportXlsx(publicUuid, includeWithdrawn, includeTransferred);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"registro-notas.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/academic/teacher-assignments/{publicUuid}/gradebook/export.pdf")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
    @Operation(summary = "Export grade book as PDF")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable UUID publicUuid,
            @RequestParam(defaultValue = "false") boolean includeWithdrawn,
            @RequestParam(defaultValue = "false") boolean includeTransferred
    ) {
        byte[] body = service.exportPdf(publicUuid, includeWithdrawn, includeTransferred);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"registro-notas.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }
}
