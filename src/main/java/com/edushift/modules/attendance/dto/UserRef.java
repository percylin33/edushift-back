package com.edushift.modules.attendance.dto;

import java.util.UUID;

/**
 * Compact reference to an internal user (the docente that scanned or
 * edited a record). Used as a nested field in
 * {@link AttendanceRecordResponse} so the FE can show a chip "Profe
 * Juan" without a second roundtrip (Sprint 6 / BE-6.2.D).
 */
public record UserRef(UUID publicUuid, String fullName) {
}
