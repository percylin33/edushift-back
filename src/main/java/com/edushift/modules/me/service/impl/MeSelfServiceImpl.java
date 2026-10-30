package com.edushift.modules.me.service.impl;

import com.edushift.modules.attendance.dto.AttendanceQrInfo;
import com.edushift.modules.attendance.entity.AttendanceRecord;
import com.edushift.modules.attendance.repository.AttendanceRecordRepository;
import com.edushift.modules.attendance.service.AttendanceExportService;
import com.edushift.modules.attendance.service.AttendanceQrService;
import com.edushift.modules.attendance.service.AttendanceQrService.IssuedQr;
import com.edushift.modules.attendance.service.QrRenderer;
import com.edushift.modules.me.dto.MeAttendanceRecordResponse;
import com.edushift.modules.me.dto.MeQrResponse;
import com.edushift.modules.me.service.MeSelfService;
import com.edushift.modules.payments.dto.InvoiceResponse;
import com.edushift.modules.payments.service.PaymentService;
import com.edushift.modules.schedule.timeslot.dto.ScheduleWeekView;
import com.edushift.modules.schedule.timeslot.service.TimeSlotService;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.modules.reports.entity.ReportJob;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeSelfServiceImpl implements MeSelfService {

    private final CurrentUserProvider currentUserProvider;
    private final StudentRepository studentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceQrService attendanceQrService;
    private final QrRenderer qrRenderer;
    private final AttendanceExportService attendanceExportService;
    private final PaymentService paymentService;
    private final TimeSlotService timeSlotService;

    @Override
    @Transactional(readOnly = true)
    public List<MeAttendanceRecordResponse> listMyAttendance(LocalDate from, LocalDate to) {
        Student student = requireStudent();
        Instant fromInstant = from != null
                ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null
                ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        return attendanceRecordRepository.findByStudentInRange(student, fromInstant, toInstant).stream()
                .map(this::toAttendance)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportMyAttendance(LocalDate from, LocalDate to, ReportJob.Format format)
            throws IOException {
        Student student = requireStudent();
        Instant fromInstant = from != null
                ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null
                ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        List<AttendanceRecord> records =
                attendanceRecordRepository.findByStudentInRange(student, fromInstant, toInstant);
        return attendanceExportService.exportRecords(
                records, format, "Mi asistencia — " + student.fullName());
    }

    @Override
    @Transactional(readOnly = true)
    public MeQrResponse getMyQr() {
        Student student = requireStudent();
        AttendanceQrInfo info = attendanceQrService.getInfoForCaller(student.getPublicUuid());
        if (info == null) {
            return new MeQrResponse(student.getPublicUuid(), null, false, null);
        }
        String token = attendanceQrService.peekActiveTokenForCaller(student.getPublicUuid());
        return new MeQrResponse(
                info.studentPublicUuid(),
                info.issuedAt(),
                true,
                token == null ? null : toSvgDataUri(token));
    }

    @Override
    @Transactional
    public MeQrResponse revealMyQr() {
        Student student = requireStudent();
        IssuedQr issued = attendanceQrService.getOrIssueQrForCaller(student.getPublicUuid());
        return new MeQrResponse(
                issued.info().studentPublicUuid(),
                issued.info().issuedAt(),
                true,
                toSvgDataUri(issued.jwt()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> listMyPayments() {
        UUID userId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new NotFoundException(
                        "ME_NOT_AUTHENTICATED", "Authenticated user is required"));
        requireStudent();
        return paymentService.listInvoicesForCaller(userId, Pageable.unpaged()).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleWeekView listMySchedule(UUID periodUuid) {
        return timeSlotService.getScheduleWeekForStudent(requireStudent(), periodUuid);
    }

    private String toSvgDataUri(String token) {
        byte[] svg = qrRenderer.renderSvg(token);
        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg);
    }

    private Student requireStudent() {
        UUID userId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new NotFoundException(
                        "ME_NOT_AUTHENTICATED", "Authenticated user is required"));
        return studentRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        "ME_NOT_A_STUDENT",
                        "Caller has no student record in this tenant"));
    }

    private MeAttendanceRecordResponse toAttendance(AttendanceRecord record) {
        return new MeAttendanceRecordResponse(
                record.getPublicUuid(),
                record.getSession() != null ? record.getSession().getPublicUuid() : null,
                record.getStatus(),
                record.getOccurredAt(),
                record.getNotes(),
                record.getJustificationStatus(),
                record.getJustificationText(),
                record.getApprovedAt());
    }
}
