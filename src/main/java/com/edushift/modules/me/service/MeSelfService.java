package com.edushift.modules.me.service;

import com.edushift.modules.me.dto.MeAttendanceRecordResponse;
import com.edushift.modules.me.dto.MeQrResponse;
import com.edushift.modules.payments.dto.InvoiceResponse;
import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.schedule.timeslot.dto.ScheduleWeekView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * STUDENT self-service surface that is not academic listing
 * (attendance, QR credential, invoices). Identity always from JWT.
 */
public interface MeSelfService {

    List<MeAttendanceRecordResponse> listMyAttendance(LocalDate from, LocalDate to);

    byte[] exportMyAttendance(LocalDate from, LocalDate to, ReportJob.Format format) throws java.io.IOException;

    MeQrResponse getMyQr();

    MeQrResponse revealMyQr();

    List<InvoiceResponse> listMyPayments();

    ScheduleWeekView listMySchedule(UUID periodUuid);
}
