/**
 * <strong>academic.unit sub-module</strong> — pedagogical units that hang
 * off a course. Sprint 5A / BE-5A.1.
 *
 * <p>A {@code Unit} is the bridge between {@code Course} and
 * {@code LearningSession}: a course contains 1..N ordered units, and each
 * session anchors on exactly one unit (Sprint 5A / BE-5A.4). Units are
 * tenant-scoped via Hibernate's {@code @TenantId} discriminator and
 * inherit audit / soft-delete from {@link com.edushift.shared.domain.TenantAwareEntity}.</p>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>{@code (course_id, lower(name))} is unique on non-deleted rows.</li>
 *   <li>{@code display_order} is unique inside a course on non-deleted rows
 *       and mutated atomically via a two-pass reorder strategy that mirrors
 *       {@code GradeReorderRequest} (BE-4.2).</li>
 *   <li>A unit must be empty of {@code LearningSession}s before it can be
 *       hard soft-deleted ({@code UNIT_HAS_SESSIONS}, 409). Deactivation
 *       ({@code is_active=false}) is always allowed.</li>
 * </ul>
 *
 * <h3>Downstream</h3>
 * <ul>
 *   <li>{@code learning_sessions} (BE-5A.4) FKs on {@code unit_id} with
 *       RESTRICT — service emits {@code UNIT_HAS_SESSIONS} when applicable.</li>
 *   <li>{@code reports} (Sprint 9) will aggregate session metrics by unit.</li>
 * </ul>
 */
package com.edushift.modules.academic.unit;
