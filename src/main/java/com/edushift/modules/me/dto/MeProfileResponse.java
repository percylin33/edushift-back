package com.edushift.modules.me.dto;

import java.util.List;
import java.util.UUID;

/**
 * Personal profile envelope surfaced at {@code GET /api/v1/me/profile}
 * (DEBT-STUDENT-PRIVACY / Fase 1).
 *
 * <p>Everything here is derived from the JWT — the caller cannot
 * influence the response by passing {@code studentPublicUuid} from the
 * body. The frontend uses this as the source of truth for the STUDENT
 * shell (avatar, display name, section badge, tenant branding, the list
 * of sub-section permissions the user holds).</p>
 *
 * @param publicUuid          the caller's {@code students.publicUuid}
 * @param userPublicUuid      the caller's {@code users.publicUuid}
 * @param firstName           the student's first name (display)
 * @param lastName            the student's last name (display)
 * @param email               the student's email (if any)
 * @param sectionPublicUuid   the section the student is currently enrolled in
 * @param sectionName         friendly name of that section
 * @param gradeName           friendly name of the grade (e.g. "4.° de secundaria")
 * @param academicYearLabel   the academic year label (e.g. "2026")
 * @param tenantPublicUuid    the caller's tenant publicUuid
 * @param tenantName          the tenant display name
 * @param tenantLogoUrl       the tenant logo URL (may be null)
 * @param permissions         the list of {@code ME_*} authorities the
 *                            caller holds — drives the conditional
 *                            rendering of the "Mi asistencia" /
 *                            "Mis pagos" cards on the home page.
 */
public record MeProfileResponse(
        UUID publicUuid,
        UUID userPublicUuid,
        String firstName,
        String lastName,
        String email,
        UUID sectionPublicUuid,
        String sectionName,
        String gradeName,
        String academicYearLabel,
        UUID tenantPublicUuid,
        String tenantName,
        String tenantLogoUrl,
        List<String> permissions
) {
}
