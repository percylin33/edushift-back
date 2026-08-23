package com.edushift.modules.me.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.attendance.dto.AttendanceQrInfo;
import com.edushift.modules.attendance.entity.AttendanceRecord;
import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.attendance.repository.AttendanceRecordRepository;
import com.edushift.modules.attendance.service.AttendanceQrService;
import com.edushift.modules.attendance.service.QrRenderer;
import com.edushift.modules.me.dto.MeAttendanceRecordResponse;
import com.edushift.modules.me.dto.MeQrResponse;
import com.edushift.modules.payments.service.PaymentService;
import com.edushift.modules.schedule.timeslot.dto.ScheduleWeekView;
import com.edushift.modules.schedule.timeslot.service.TimeSlotService;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MeSelfServiceImplTest {

    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private StudentRepository studentRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private AttendanceQrService attendanceQrService;
    @Mock private QrRenderer qrRenderer;
    @Mock private PaymentService paymentService;
    @Mock private TimeSlotService timeSlotService;
    @InjectMocks private MeSelfServiceImpl service;

    @Test
    void listMyAttendanceReturnsOnlyCallerRows() {
        UUID userId = UUID.randomUUID();
        Student student = student(userId);
        AttendanceRecord record = new AttendanceRecord();
        record.setPublicUuid(UUID.randomUUID());
        record.setStatus(AttendanceRecordStatus.ABSENT);
        record.setOccurredAt(Instant.parse("2026-08-01T12:00:00Z"));
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(userId));
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
        when(attendanceRecordRepository.findByStudentInRange(student, null, null))
                .thenReturn(List.of(record));

        List<MeAttendanceRecordResponse> result = service.listMyAttendance();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(AttendanceRecordStatus.ABSENT);
        verify(attendanceRecordRepository).findByStudentInRange(student, null, null);
    }

    @Test
    void getMyQrDoesNotRotate() {
        UUID userId = UUID.randomUUID();
        Student student = student(userId);
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(userId));
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
        when(attendanceQrService.getInfoForCaller(student.getPublicUuid()))
                .thenReturn(new AttendanceQrInfo(student.getPublicUuid(), Instant.now(), null, null));

        MeQrResponse qr = service.getMyQr();

        assertThat(qr.hasCredential()).isTrue();
        assertThat(qr.svgDataUri()).isNull();
        verify(attendanceQrService).getInfoForCaller(student.getPublicUuid());
    }

    @Test
    void parentWithoutStudentRecordIsNotFound() {
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(userId));
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(service::listMyAttendance)
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listMyPaymentsDelegatesToCallerScopedPaymentService() {
        UUID userId = UUID.randomUUID();
        Student student = student(userId);
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(userId));
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
        when(paymentService.listInvoicesForCaller(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.listMyPayments()).isEmpty();
    }

    @Test
    void listMyScheduleDelegatesToTimeSlotService() {
        UUID userId = UUID.randomUUID();
        Student student = student(userId);
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(userId));
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.of(student));
        when(timeSlotService.getScheduleWeekForStudent(student, null))
                .thenReturn(ScheduleWeekView.of(List.of(), List.of()));

        assertThat(service.listMySchedule(null).slots()).isEmpty();
        verify(timeSlotService).getScheduleWeekForStudent(student, null);
    }

    private static Student student(UUID userId) {
        Student student = new Student();
        student.setPublicUuid(UUID.randomUUID());
        student.setUserId(userId);
        student.setFirstName("Lucía");
        student.setLastName("Test");
        return student;
    }
}
