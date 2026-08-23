package com.edushift.modules.me.controller;

import com.edushift.modules.me.dto.MeAttendanceRecordResponse;
import com.edushift.modules.me.dto.MeGradeResponse;
import com.edushift.modules.me.dto.MeProfileResponse;
import com.edushift.modules.me.dto.MeQrResponse;
import com.edushift.modules.me.dto.MeSectionDetailResponse;
import com.edushift.modules.me.dto.MeSectionResponse;
import com.edushift.modules.me.service.MeAcademicService;
import com.edushift.modules.me.service.MeSelfService;
import com.edushift.modules.me.service.MeService;
import com.edushift.modules.payments.dto.InvoiceResponse;
import com.edushift.modules.schedule.timeslot.dto.ScheduleSlotItem;
import com.edushift.modules.schedule.timeslot.dto.ScheduleWeekView;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.LmsAuthorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * STUDENT-facing personal surface (DEBT-STUDENT-PRIVACY / Fase 1-2).
 *
 * <p>Mounted at {@code /api/v1/me/*}. The coarse
 * {@code ME_READ} authority gates every endpoint; sub-section
 * endpoints (added in Fases 2-3) layer {@code ME_*_READ} /
 * {@code ME_*_CHECKOUT} for finer-grained overrides.</p>
 *
 * <p><b>Important client contract</b>: this controller never accepts
 * a {@code studentPublicUuid} parameter — the caller is always the
 * authenticated principal. The frontend passes the JWT only.</p>
 */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
@Tag(name = "Me",
        description = "Personal STUDENT-facing endpoints (Mis cursos, "
                + "Mis notas, Mi asistencia, Mi QR, Mis pagos, Mi perfil). "
                + "Identity is derived from the JWT; never from the body.")
public class MeController {

    private final MeService meService;
    private final MeAcademicService meAcademicService;
    private final MeSelfService meSelfService;

    /**
     * Returns the full personal profile (avatar, display name, section,
     * grade, academic year, tenant branding, granted authorities).
     */
    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.ME_READ + "')")
    @Operation(summary = "Get the caller's personal profile",
            description = "Identity derived from the JWT. Returns the linked "
                    + "Student row + the active enrollment (section / grade / "
                    + "academic year) + tenant branding + the caller's granted "
                    + "ME_*/LMS_* authorities.")
    public ApiResponse<MeProfileResponse> profile() {
        return ApiResponse.ok(meService.getProfile());
    }

    /**
     * Fase 2 / BE-ME-ACAD-1 — "Mis cursos" landing. Lists the sections
     * the caller is currently enrolled in with the per-section content
     * counts so the FE can render a complete card without a second
     * round-trip.
     */
    @GetMapping("/sections")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.ME_READ + "')")
    @Operation(summary = "List the caller's enrolled sections",
            description = "Returns one row per ACTIVE enrollment. Each row "
                    + "carries the section header (grade, level, year, "
                    + "teacher, course) and the material / task / quiz "
                    + "counts so the FE renders the 'Mis cursos' grid "
                    + "without a second request per section.")
    public ApiResponse<List<MeSectionResponse>> mySections() {
        return ApiResponse.ok(meAcademicService.listMySections());
    }

    /**
     * Fase 2 / BE-ME-ACAD-2 — "Mis cursos" detail. Returns the full
     * section header + the materials / tasks / quizzes visible to the
     * caller. Returns 404 when the caller has no ACTIVE enrollment on
     * the section (anti-enumeration: same shape as a non-existent
     * section).
     */
    @GetMapping("/sections/{sectionPublicUuid}")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.ME_READ + "')")
    @Operation(summary = "Get the caller's section detail",
            description = "Identity derived from the JWT. Returns the section "
                    + "header + the materials + the tasks + the quizzes the "
                    + "caller is allowed to see as the ACTIVE-enrolled student.")
    public ApiResponse<MeSectionDetailResponse> mySection(
            @PathVariable UUID sectionPublicUuid) {
        return ApiResponse.ok(meAcademicService.getMySection(sectionPublicUuid));
    }

    /**
     * Fase 2 / BE-ME-ACAD-3 — "Mis notas" landing. Returns every grade
     * recorded for the authenticated student, ordered by the parent
     * evaluation's scheduled date desc.
     */
    @GetMapping("/grades")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.ME_READ + "')")
    @Operation(summary = "List the caller's grades",
            description = "Returns one row per GradeRecord attached to the "
                    + "authenticated student. Each row carries the parent "
                    + "evaluation name, the section / course context, the "
                    + "numeric score (or literal) and the recorded-at stamp.")
    public ApiResponse<List<MeGradeResponse>> myGrades() {
        return ApiResponse.ok(meAcademicService.listMyGrades());
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.ME_ATTENDANCE_READ + "')")
    @Operation(summary = "List the caller's attendance records")
    public ApiResponse<List<MeAttendanceRecordResponse>> myAttendance() {
        return ApiResponse.ok(meSelfService.listMyAttendance());
    }

    @GetMapping("/qr")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.ME_QR_READ + "')")
    @Operation(summary = "Read QR credential; includes SVG when a stable printable token exists")
    public ApiResponse<MeQrResponse> myQr() {
        return ApiResponse.ok(meSelfService.getMyQr());
    }

    @PostMapping("/qr/reveal")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.ME_QR_READ + "')")
    @Operation(summary = "Show the caller's QR (issues once; reprints do not rotate)")
    public ApiResponse<MeQrResponse> revealQr() {
        return ApiResponse.ok(meSelfService.revealMyQr());
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.ME_PAYMENTS_READ + "')")
    @Operation(summary = "List invoices visible to the authenticated student")
    public ApiResponse<List<InvoiceResponse>> myPayments() {
        return ApiResponse.ok(meSelfService.listMyPayments());
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.ME_READ + "')")
    @Operation(summary = "List the caller's weekly schedule",
            description = "Identity derived from the JWT. Returns teaching slots plus "
                    + "non-teaching blocks (recess/lunch) from the day template. "
                    + "Empty enrollments return 200 with empty lists.")
    public ApiResponse<ScheduleWeekView> mySchedule(
            @RequestParam(required = false) UUID periodId) {
        return ApiResponse.ok(meSelfService.listMySchedule(periodId));
    }
}
