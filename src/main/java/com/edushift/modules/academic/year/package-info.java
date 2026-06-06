/**
 * <strong>academic.year sub-module</strong> — academic year aggregate
 * (Sprint 4 / BE-4.1).
 *
 * <p>Owns the lifecycle of {@code AcademicYear} entities: PLANNING → ACTIVE
 * → CLOSED. Enforces the invariant "at most one ACTIVE per tenant" via a
 * unique partial DB index ({@code uk_academic_years_tenant_active}).</p>
 *
 * <p>Downstream sub-modules anchor on this aggregate:</p>
 * <ul>
 *   <li>{@code academic.sections} (BE-4.3) — sections live inside an academic year.</li>
 *   <li>{@code academic.periods} (BE-4.5) — bimestres / trimestres anchored here.</li>
 *   <li>{@code teachers.assignments} (BE-4.7) — references a period (which references a year).</li>
 *   <li>{@code students.enrollments} (BE-4.8) — student matricula on a section of a year.</li>
 * </ul>
 *
 * <p>See {@code docs/product/sprints/sprint-04-teachers-academic.md} BE-4.1
 * and {@code docs/modules/academic.md} (rewrite pending: BE-4.9).</p>
 */
package com.edushift.modules.academic.year;
