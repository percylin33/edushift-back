package com.edushift.modules.attendance.teacher.service;

import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.attendance.teacher.dto.MarkTeacherAttendanceRequest;
import com.edushift.modules.attendance.teacher.dto.TeacherAttendanceResponse;
import com.edushift.modules.attendance.teacher.dto.TeacherAttendanceSummary;
import com.edushift.modules.attendance.teacher.entity.TeacherAttendanceRecord;
import com.edushift.modules.attendance.teacher.repository.TeacherAttendanceRecordRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeacherAttendanceService {

    private static final String ERROR_ALREADY_EXISTS = "TEACHER_ATTENDANCE_ALREADY_EXISTS";

    private final TeacherAttendanceRecordRepository repository;
    private final TeacherAssignmentRepository assignmentRepository;
    private final AcademicPeriodRepository periodRepository;

    @Transactional(readOnly = true)
    public List<TeacherAttendanceResponse> listByAssignment(UUID assignmentUuid) {
        TeacherAssignment assignment = loadAssignment(assignmentUuid);
        return repository.findAllByAssignment(assignment)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherAttendanceSummary summary(UUID assignmentUuid) {
        TeacherAssignment assignment = loadAssignment(assignmentUuid);
        List<Object[]> rows = repository.countByStatus(assignment);
        HashMap<Object, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put(row[0], (Long) row[1]);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return TeacherAttendanceSummary.fromCounts(counts, total);
    }

    @Transactional
    public TeacherAttendanceResponse mark(UUID assignmentUuid,
            MarkTeacherAttendanceRequest request, UUID actorUserId) {
        TeacherAssignment assignment = loadAssignment(assignmentUuid);
        AcademicPeriod period = periodRepository.findByPublicUuid(request.academicPeriodUuid())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AcademicPeriod", request.academicPeriodUuid()));

        if (repository.findByTeacherAssignmentAndScheduledDate(
                assignment, request.scheduledDate()).isPresent()) {
            throw new ConflictException(ERROR_ALREADY_EXISTS,
                    "Attendance already recorded for this assignment on "
                            + request.scheduledDate());
        }

        TeacherAttendanceRecord entity = new TeacherAttendanceRecord();
        entity.setTeacherAssignment(assignment);
        entity.setAcademicPeriod(period);
        entity.setScheduledDate(request.scheduledDate());
        entity.setStatus(request.status());
        entity.setNotes(request.notes());
        entity.setRecordedByUserId(actorUserId);
        TeacherAttendanceRecord saved = repository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID publicUuid) {
        TeacherAttendanceRecord entity = repository.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TeacherAttendanceRecord", publicUuid));
        repository.delete(entity);
    }

    private TeacherAssignment loadAssignment(UUID uuid) {
        return assignmentRepository.findByPublicUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TeacherAssignment", uuid));
    }

    private TeacherAttendanceResponse toResponse(TeacherAttendanceRecord r) {
        String teacherName = "";
        if (r.getTeacherAssignment() != null
                && r.getTeacherAssignment().getTeacher() != null) {
            teacherName = r.getTeacherAssignment().getTeacher().getFirstName()
                    + " " + r.getTeacherAssignment().getTeacher().getLastName();
        }
        return new TeacherAttendanceResponse(
                r.getPublicUuid(),
                r.getTeacherAssignment().getPublicUuid(),
                r.getAcademicPeriod().getPublicUuid(),
                r.getScheduledDate(),
                r.getStatus(),
                r.getNotes(),
                r.getRecordedByUserId(),
                teacherName
        );
    }
}
