package com.edushift.modules.me.service;

import com.edushift.modules.me.dto.MeProfileResponse;

/**
 * STUDENT-facing {@code /me/*} service surface (DEBT-STUDENT-PRIVACY /
 * Fase 1).
 *
 * <p>All entry points derive the student identity from the JWT — the
 * caller never passes {@code studentPublicUuid} in the body. Implementations
 * MUST throw {@code BusinessException} / {@code NotFoundException} when
 * the caller has no student record (e.g. PARENT/TEACHER who hits this
 * service by mistake); the controller layer is responsible for
 * surfacing a 404 / 403 with a clear message.</p>
 */
public interface MeService {

    /**
     * Returns the full personal profile envelope used by the STUDENT
     * shell (avatar, display name, section badge, tenant branding, the
     * list of {@code ME_*} permissions the caller holds).
     */
    MeProfileResponse getProfile();
}
