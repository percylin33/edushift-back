/**
 * <strong>academic.section sub-module</strong> — physical class groups
 * inside a {@code (AcademicYear, Grade)} tuple. Sprint 4 / BE-4.3.
 *
 * <p>Sections are the anchor of {@code student_enrollments} (BE-4.8)
 * and of {@code teacher_assignments} (BE-4.7), which in turn feed
 * {@code Session} (Sprint 5) for attendance.</p>
 *
 * <h3>Lifecycle inheritance</h3>
 * Sections inherit the lifecycle of their parent
 * {@link com.edushift.modules.academic.year.entity.AcademicYearStatus}:
 * writes are rejected with {@code ACADEMIC_YEAR_LOCKED} on CLOSED years.
 * Reads are always allowed for historical analytics.
 *
 * <h3>Tenant scoping</h3>
 * Tenant-aware via Hibernate's {@code @TenantId} discriminator. The
 * service runs an explicit triple-check (year.tenant, grade.tenant,
 * current context) on every write for defense in depth.
 */
package com.edushift.modules.academic.section;
