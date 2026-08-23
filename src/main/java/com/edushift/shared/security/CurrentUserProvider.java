package com.edushift.shared.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the current authenticated user from the security context.
 *
 * <p>The default implementation lives in
 * {@code edushift.infrastructure.security.SecurityContextCurrentUserProvider}
 * and reads the principal from Spring's {@code SecurityContextHolder}.</p>
 *
 * <p>The user is identified by a UUID (public id, not the internal
 * {@code Long} primary key — see ADR-1.2 about UUID v7 as the
 * public identifier).</p>
 */
public interface CurrentUserProvider {

    /** UUID of the current authenticated user, empty if anonymous. */
    Optional<UUID> currentUserId();

    /** Username of the current authenticated user, empty if anonymous. */
    Optional<String> currentUsername();

    /** Tenant id of the current authenticated user, empty if anonymous. */
    Optional<UUID> currentTenantId();

    /**
     * Role of the current authenticated user, empty if anonymous.
     * Used by services that need fine-grained ownership checks (e.g.
     * STUDENT-only branches) without re-reading the JWT authorities.
     */
    default Optional<com.edushift.modules.auth.entity.UserRole> currentUserRole() {
        return Optional.empty();
    }

    /**
     * DEBT-STUDENT-PRIVACY (Fase 0.7): {@code students.publicUuid} for
     * the caller, if the caller is a STUDENT. Empty for non-students
     * (parents, teachers, admins). Used by the {@code /me/*} endpoints
     * to derive the caller's student row without accepting a
     * {@code studentUuid} parameter from the request body.
     *
     * <p>The default impl returns empty; concrete wiring lives in
     * {@code SecurityContextCurrentUserProvider} (BE-phase-6).</p>
     */
    default Optional<UUID> currentStudentPublicUuid() {
        return Optional.empty();
    }
}
