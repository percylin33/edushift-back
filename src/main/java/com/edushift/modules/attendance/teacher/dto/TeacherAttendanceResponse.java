package com.edushift.modules.attendance.teacher.dto;

import com.edushift.modules.attendance.teacher.entity.TeacherAttendanceRecord.Status;
import java.time.LocalDate;
import java.util.UUID;

public record TeacherAttendanceResponse(
    UUID publicUuid,
    UUID teacherAssignmentUuid,
    UUID academicPeriodUuid,
    LocalDate scheduledDate,
    Status status,
    String notes,
    UUID recordedByUserId,
    String teacherName
) {}
