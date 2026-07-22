package com.edushift.modules.teachers.assignments.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by {@code TeacherAssignmentServiceImpl.createAssignment} right
 * after the {@code teacher_assignments} row is flushed, but BEFORE the outer
 * transaction commits — so that in-tx {@code @EventListener} consumers (the
 * workload listener) and in-tx dispatched {@code NotificationEvent}s (teacher
 * + students fan-out) participate in the same Hibernate session.
 *
 * <p>Listeners see the correct tenant scope because the publisher runs inside
 * the request's tenant context (Hibernate's {@code @TenantId} discriminator
 * is already populated for the create call).</p>
 *
 * <p>Sprint 5 (DEBT-TEA-1 cascade). The event is intentionally minimal — all
 * payloads use public UUIDs, never internal ids, to keep the contract
 * cross-module safe.</p>
 *
 * @param assignmentPublicUuid    new assignment row's external id
 * @param teacherPublicUuid       the assigned teacher
 * @param sectionPublicUuid       the section being taught
 * @param coursePublicUuid        the course being taught
 * @param academicPeriodPublicUuid the bimestre/period
 * @param tenantId                tenant scope of the publisher (matches the
 *                                runAs context; listeners should wrap any
 *                                cross-module JPA work in
 *                                {@code TenantContext.runAs(...)} if they
 *                                fire {@code AFTER_COMMIT})
 * @param occurredAt              when the event was raised
 */
public record TeacherAssignmentCreatedEvent(
		UUID assignmentPublicUuid,
		UUID teacherPublicUuid,
		UUID sectionPublicUuid,
		UUID coursePublicUuid,
		UUID academicPeriodPublicUuid,
		UUID tenantId,
		Instant occurredAt
) {
}
