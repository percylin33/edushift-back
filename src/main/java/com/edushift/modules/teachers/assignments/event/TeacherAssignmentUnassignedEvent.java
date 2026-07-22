package com.edushift.modules.teachers.assignments.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by {@code TeacherAssignmentServiceImpl.softEnd} when a teacher
 * assignment is soft-ended (sets {@code unassignedAt = NOW()} and saves).
 * Sprint 5 / DEBT-TEA-3 closes the cascade in the other direction:
 *
 * <ul>
 *   <li>{@code TeacherAssignmentUnassignedWorkloadListener} decrements the
 *       teacher's {@code assignments_count} atomically (so the counter
 *       stays in lock-step with the {@code teacher_assignments} table).</li>
 * </ul>
 *
 * <p>Mirrors {@link TeacherAssignmentCreatedEvent} by contract: same field
 * set, same tenant scope, same public-UUID-only convention.</p>
 */
public record TeacherAssignmentUnassignedEvent(
		UUID assignmentPublicUuid,
		UUID teacherPublicUuid,
		UUID sectionPublicUuid,
		UUID coursePublicUuid,
		UUID academicPeriodPublicUuid,
		UUID tenantId,
		Instant unassignedAt
) {
}
