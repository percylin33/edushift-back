package com.edushift.modules.attendance.teacher.controller;

import com.edushift.modules.attendance.teacher.dto.MarkTeacherAttendanceRequest;
import com.edushift.modules.attendance.teacher.dto.TeacherAttendanceResponse;
import com.edushift.modules.attendance.teacher.dto.TeacherAttendanceSummary;
import com.edushift.modules.attendance.teacher.service.TeacherAttendanceService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.CurrentUserProvider;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Teacher Attendance",
     description = "Teacher attendance per assignment — Sprint 18 / BE-18.4")
public class TeacherAttendanceController {

    private final TeacherAttendanceService service;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/teacher-assignments/{assignmentUuid}/attendance")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TEACHER')")
    @Operation(summary = "List attendance records for a teacher assignment")
    public ResponseEntity<ApiResponse<List<TeacherAttendanceResponse>>> list(
            @PathVariable UUID assignmentUuid) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.listByAssignment(assignmentUuid)));
    }

    @GetMapping("/teacher-assignments/{assignmentUuid}/attendance/summary")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TEACHER')")
    @Operation(summary = "Attendance summary (counts + %) for a teacher assignment")
    public ResponseEntity<ApiResponse<TeacherAttendanceSummary>> summary(
            @PathVariable UUID assignmentUuid) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.summary(assignmentUuid)));
    }

    @PostMapping("/teacher-assignments/{assignmentUuid}/attendance")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'TEACHER')")
    @Operation(summary = "Mark teacher attendance for a specific date")
    public ResponseEntity<ApiResponse<TeacherAttendanceResponse>> mark(
            @PathVariable UUID assignmentUuid,
            @Valid @RequestBody MarkTeacherAttendanceRequest request) {
        UUID actorUserId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new RuntimeException("Authenticated user required"));
        TeacherAttendanceResponse response = service.mark(assignmentUuid, request, actorUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response));
    }

    @DeleteMapping("/teacher-assignments/{assignmentUuid}/attendance/{publicUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Delete a teacher attendance record (TENANT_ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable UUID publicUuid) {
        service.delete(publicUuid);
        return ResponseEntity.noContent().build();
    }
}
