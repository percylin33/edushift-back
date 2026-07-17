package com.edushift.modules.attendance.teacher.dto;

import com.edushift.modules.attendance.teacher.entity.TeacherAttendanceRecord.Status;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record MarkTeacherAttendanceRequest(
    @NotNull UUID academicPeriodUuid,
    @NotNull LocalDate scheduledDate,
    @NotNull Status status,
    String notes
) {}
