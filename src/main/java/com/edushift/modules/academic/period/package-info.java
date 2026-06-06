/**
 * <strong>academic.period sub-module</strong> — academic periods
 * (BIMESTRE / TRIMESTRE / ANUAL) hanging off an
 * {@link com.edushift.modules.academic.year.entity.AcademicYear}.
 * Sprint 4 / BE-4.5.
 *
 * <p>Periods anchor downstream features:
 * <ul>
 *   <li>Teacher assignments (BE-4.7) — a teacher is assigned a course
 *       at a section <em>per period</em>.</li>
 *   <li>Grade reports (Sprint 7) — final marks are aggregated per
 *       {@code (Course × Section × Period)}.</li>
 * </ul>
 *
 * <h3>Invariants enforced at the service layer</h3>
 * <ul>
 *   <li>Ordinals are 1..N within {@code (year, type)}, unique and
 *       <em>contiguous</em> (no gaps).</li>
 *   <li>Date ranges within the same {@code (year, type)} MUST NOT
 *       overlap — checked with Postgres' {@code daterange &&}.</li>
 *   <li>{@code [startDate, endDate]} ⊆ {@code [year.startDate,
 *       year.endDate]}.</li>
 *   <li>Closed years reject every write with {@code ACADEMIC_YEAR_LOCKED}.</li>
 * </ul>
 */
package com.edushift.modules.academic.period;
