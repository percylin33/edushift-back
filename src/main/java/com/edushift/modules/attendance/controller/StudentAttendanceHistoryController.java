package com.edushift.modules.attendance.controller;

import com.edushift.modules.attendance.dto.StudentAttendanceHistoryResponse;
import com.edushift.modules.attendance.service.AttendanceService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/students/{studentPublicUuid}/attendance")
@RequiredArgsConstructor
@Tag(name = "Student attendance history")
public class StudentAttendanceHistoryController {

    private final AttendanceService attendanceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER','STAFF')")
    @Operation(summary = "List attendance records for a student (staff)")
    public ApiResponse<List<StudentAttendanceHistoryResponse>> list(
            @PathVariable UUID studentPublicUuid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(attendanceService.listStudentAttendanceHistory(studentPublicUuid, from, to));
    }
}
