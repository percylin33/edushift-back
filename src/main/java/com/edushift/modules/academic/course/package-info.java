/**
 * <strong>academic.course sub-module</strong> — course catalog with M:N
 * association to academic levels. Sprint 4 / BE-4.4.
 *
 * <p>The pivot is modelled as the explicit
 * {@link com.edushift.modules.academic.course.entity.CourseLevel} entity
 * (NOT a {@code @ManyToMany} join table) so every row carries
 * {@code tenant_id} via Hibernate's {@code @TenantId} discriminator and
 * benefits from the audit machinery in {@code BaseEntity}.</p>
 *
 * <h3>Downstream</h3>
 * <ul>
 *   <li>{@code teachers.assignments} (BE-4.7) will reference courses;
 *       deleting a course used in an assignment activates the
 *       {@code COURSE_IN_USE_BY_ASSIGNMENTS} error contract.</li>
 *   <li>{@code grades-reports} (Sprint 7) will anchor on
 *       {@code (Course × Section × Period)}.</li>
 * </ul>
 */
package com.edushift.modules.academic.course;
