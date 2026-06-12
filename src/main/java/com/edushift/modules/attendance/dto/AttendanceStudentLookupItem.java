package com.edushift.modules.attendance.dto;

import java.util.UUID;

/**
 * Lean projection returned by
 * {@code GET /api/v1/attendance/students/lookup}
 * (Sprint 6 / BE-6.8 manual fallback).
 *
 * <p>Designed for the "auxiliary in the entrance" picker: just enough
 * to disambiguate a student and confirm "yes, that's the kid in front
 * of me" without exposing PII (no email, no birthDate, no address).
 *
 * <p>{@code documentNumber} is kept because Peruvian schools routinely
 * have homonyms in the same grade — the document number is the natural
 * tiebreaker the auxiliary will reach for.
 *
 * @param studentPublicUuid the publicUuid to send to
 *                          {@code POST /attendance/manual-check-in}.
 * @param firstName         normalized title-case first name.
 * @param lastName          normalized title-case last name.
 * @param fullName          {@code firstName + " " + lastName} (server-
 *                          computed for FE convenience).
 * @param documentNumber    natural id, used for tiebreaking on homonyms.
 * @param sectionPublicUuid resolved from the student's current ACTIVE
 *                          enrollment.
 * @param sectionName       e.g. {@code "A"}.
 * @param gradeName         e.g. {@code "5to"}.
 * @param levelName         e.g. {@code "Primaria"}.
 */
public record AttendanceStudentLookupItem(
		UUID studentPublicUuid,
		String firstName,
		String lastName,
		String fullName,
		String documentNumber,
		UUID sectionPublicUuid,
		String sectionName,
		String gradeName,
		String levelName
) {
}
