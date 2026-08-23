package com.edushift.modules.me.service.impl;

import com.edushift.modules.attendance.dto.AttendanceQrInfo;
import com.edushift.modules.attendance.entity.AttendanceRecord;
import com.edushift.modules.attendance.repository.AttendanceRecordRepository;
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
    private final PaymentService paymentService;
    private final TimeSlotService timeSlotService;

    @Override
    @Transactional(readOnly = true)
    public List<MeAttendanceRecordResponse> listMyAttendance() {
        Student student = requireStudent();
        return attendanceRecordRepository.findByStudentInRange(student, null, null).stream()
                .map(this::toAttendance)
                .toList();
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
