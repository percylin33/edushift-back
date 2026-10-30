package com.edushift.modules.reports.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Parsed {@code report_jobs.params} for {@code ATTENDANCE_SUMMARY}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AttendanceReportParams(
        String scope,
        UUID sectionPublicUuid,
        List<UUID> sectionPublicUuids,
        UUID studentPublicUuid,
        UUID sessionPublicUuid,
        LocalDate from,
        LocalDate to,
        Integer month,
        Integer year
) {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public static AttendanceReportParams empty() {
        return new AttendanceReportParams(null, null, null, null, null, null, null, null, null);
    }

    public static AttendanceReportParams parse(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            AttendanceReportParams raw = MAPPER.readValue(json, AttendanceReportParams.class);
            return raw.withResolvedDates();
        } catch (Exception e) {
            return empty();
        }
    }

    private AttendanceReportParams withResolvedDates() {
        if (from != null && to != null) {
            return this;
        }
        if (month != null && year != null) {
            YearMonth ym = YearMonth.of(year, month);
            return new AttendanceReportParams(
                    scope, sectionPublicUuid, sectionPublicUuids, studentPublicUuid,
                    sessionPublicUuid, ym.atDay(1), ym.atEndOfMonth(), month, year);
        }
        return this;
    }

    public boolean isStudentScope() {
        return studentPublicUuid != null || "STUDENT".equalsIgnoreCase(scope);
    }
}
