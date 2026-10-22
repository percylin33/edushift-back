package com.edushift.modules.family.controller;

import com.edushift.modules.family.dto.FamilyActivityItemDto;
import com.edushift.modules.family.dto.FamilyAttendanceRecordDto;
import com.edushift.modules.family.dto.FamilyChildSummary;
import com.edushift.modules.family.dto.FamilyCompetencyResultDto;
import com.edushift.modules.family.dto.FamilyGradeItemDto;
import com.edushift.modules.family.dto.FamilyPaymentItemDto;
import com.edushift.modules.family.service.FamilyService;
import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.schedule.timeslot.dto.ScheduleWeekView;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
// BUG-2026-08-10-03: was "/v1/family" — the global WebConfiguration adds
// "/v1" to every controller, so the previous mapping resulted in
// /api/v1/v1/family. Restricting to "/family" keeps the prefix collision
// consistent with the rest of the codebase (Attendance, GradeRecord, etc.).
@RequestMapping("/family")
@RequiredArgsConstructor
public class FamilyController {
	private final FamilyService familyService;
	private final CurrentUserProvider currentUserProvider;

	@GetMapping("/children")
	@PreAuthorize("hasAuthority('LMS_FAMILY_READ')")
	public ResponseEntity<ApiResponse<List<FamilyChildSummary>>> listChildren() {
		UUID userId = currentUserProvider.currentUserId()
				.orElseThrow(() -> new IllegalStateException("Authenticated user is required"));
		return ResponseEntity.ok(ApiResponse.ok(familyService.listChildren(userId)));
	}

	@GetMapping("/children/{studentPublicUuid}/attendance")
	@PreAuthorize("hasAuthority('LMS_FAMILY_READ')")
	public ResponseEntity<Page<FamilyAttendanceRecordDto>> getChildAttendance(
			@PathVariable UUID studentPublicUuid,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@ParameterObject Pageable pageable) {
		UUID userId = currentUserProvider.currentUserId()
				.orElseThrow(() -> new IllegalStateException("Authenticated user is required"));
		return ResponseEntity.ok(
				familyService.getChildAttendance(studentPublicUuid, userId, from, to, pageable));
	}

	@GetMapping("/children/{studentPublicUuid}/attendance/export")
	@PreAuthorize("hasAuthority('LMS_FAMILY_READ')")
	public ResponseEntity<byte[]> exportChildAttendance(
			@PathVariable UUID studentPublicUuid,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "PDF") ReportJob.Format format) throws java.io.IOException {
		UUID userId = currentUserProvider.currentUserId()
				.orElseThrow(() -> new IllegalStateException("Authenticated user is required"));
		byte[] body = familyService.exportChildAttendance(studentPublicUuid, userId, from, to, format);
		String ext = format.name().toLowerCase();
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=asistencia-hijo." + ext)
				.contentType(contentTypeFor(format))
				.body(body);
	}

	private static MediaType contentTypeFor(ReportJob.Format format) {
		return switch (format) {
			case PDF -> MediaType.APPLICATION_PDF;
			case XLSX -> MediaType.parseMediaType(
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			case CSV -> MediaType.parseMediaType("text/csv");
		};
	}

	@GetMapping("/children/{studentPublicUuid}/grades")
	@PreAuthorize("hasAuthority('LMS_FAMILY_READ')")
	public ResponseEntity<ApiResponse<List<FamilyGradeItemDto>>> getChildGrades(
			@PathVariable UUID studentPublicUuid) {
		UUID userId = currentUserProvider.currentUserId()
				.orElseThrow(() -> new IllegalStateException("Authenticated user is required"));
		return ResponseEntity.ok(ApiResponse.ok(familyService.getChildGrades(studentPublicUuid, userId)));
	}

	@GetMapping("/children/{studentPublicUuid}/competency-results")
	@PreAuthorize("hasAuthority('LMS_FAMILY_READ')")
	public ResponseEntity<ApiResponse<List<FamilyCompetencyResultDto>>> getChildCompetencyResults(
			@PathVariable UUID studentPublicUuid) {
		UUID userId = currentUserProvider.currentUserId()
				.orElseThrow(() -> new IllegalStateException("Authenticated user is required"));
		return ResponseEntity.ok(ApiResponse.ok(
				familyService.getChildCompetencyResults(studentPublicUuid, userId)));
	}

	@GetMapping("/children/{studentPublicUuid}/activities")
	@PreAuthorize("hasAuthority('LMS_FAMILY_READ')")
	public ResponseEntity<ApiResponse<List<FamilyActivityItemDto>>> getChildActivities(
			@PathVariable UUID studentPublicUuid) {
		UUID userId = currentUserProvider.currentUserId()
				.orElseThrow(() -> new IllegalStateException("Authenticated user is required"));
		return ResponseEntity.ok(ApiResponse.ok(
				familyService.getChildActivities(studentPublicUuid, userId)));
	}

	@GetMapping("/children/{studentPublicUuid}/payments")
	@PreAuthorize("hasAuthority('LMS_FAMILY_READ')")
	public ResponseEntity<ApiResponse<List<FamilyPaymentItemDto>>> getChildPayments(
			@PathVariable UUID studentPublicUuid) {
		UUID userId = currentUserProvider.currentUserId()
				.orElseThrow(() -> new IllegalStateException("Authenticated user is required"));
		return ResponseEntity.ok(ApiResponse.ok(
				familyService.getChildPayments(studentPublicUuid, userId)));
	}

	@GetMapping("/children/{studentPublicUuid}/schedule")
	@PreAuthorize("hasAuthority('LMS_FAMILY_READ')")
	public ResponseEntity<ApiResponse<ScheduleWeekView>> getChildSchedule(
			@PathVariable UUID studentPublicUuid,
			@RequestParam(required = false) UUID periodId) {
		UUID userId = currentUserProvider.currentUserId()
				.orElseThrow(() -> new IllegalStateException("Authenticated user is required"));
		return ResponseEntity.ok(ApiResponse.ok(
				familyService.getChildSchedule(studentPublicUuid, userId, periodId)));
	}
}
