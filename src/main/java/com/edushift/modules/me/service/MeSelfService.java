package com.edushift.modules.me.service;

import com.edushift.modules.me.dto.MeAttendanceRecordResponse;
import com.edushift.modules.me.dto.MeQrResponse;
import com.edushift.modules.payments.dto.InvoiceResponse;
import com.edushift.modules.schedule.timeslot.dto.ScheduleWeekView;
import java.util.List;
import java.util.UUID;

/**
 * STUDENT self-service surface that is not academic listing
 * (attendance, QR credential, invoices). Identity always from JWT.
 */
public interface MeSelfService {

    List<MeAttendanceRecordResponse> listMyAttendance();

    MeQrResponse getMyQr();

    MeQrResponse revealMyQr();

    List<InvoiceResponse> listMyPayments();

    ScheduleWeekView listMySchedule(UUID periodUuid);
}
