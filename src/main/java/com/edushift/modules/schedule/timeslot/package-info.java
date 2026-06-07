/**
 * Schedule — weekly recurring time slots per {@code TeacherAssignment}
 * (Sprint 5A / BE-5A.3).
 *
 * <h2>Purpose</h2>
 * <p>A {@code TimeSlot} answers <em>"when does teacher X teach course Y
 * to section Z?"</em> as a weekly pattern. Concrete daily occurrences
 * are materialised by {@code learning_sessions} (BE-5A.4) which carry
 * a {@code scheduled_date} and reference the parent assignment.</p>
 *
 * <h2>Why time-only (no date)</h2>
 * <ul>
 *   <li>The school year repeats the schedule every week; storing dates
 *       would force re-entry every term.</li>
 *   <li>Holidays, makeup classes, and per-day deviations live in
 *       {@code learning_sessions} where they belong.</li>
 *   <li>Switching to a fixed weekly pattern keeps the FE rendering
 *       cheap (just plot 7 columns).</li>
 * </ul>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><strong>day_of_week ∈ [1..7]</strong> — ISO-8601, enforced by
 *       {@code chk_time_slots_day_of_week} CHECK.</li>
 *   <li><strong>end_time &gt; start_time</strong> — enforced by
 *       {@code chk_time_slots_time_range} CHECK; slots cannot cross
 *       midnight (split into two if needed).</li>
 *   <li><strong>No overlap inside the same assignment + day</strong> —
 *       enforced at the service layer (overlap = "ranges {@code (a,b)}
 *       and {@code (c,d)}" share at least one minute, i.e.
 *       {@code a &lt; d AND c &lt; b}). Cross-assignment / cross-teacher
 *       overlap is intentionally NOT validated (DEBT-SCH-1).</li>
 *   <li><strong>FK ON DELETE RESTRICT</strong> on
 *       {@code teacher_assignment_id} — slots cannot dangle; soft-end
 *       the assignment to retire all its slots together.</li>
 * </ul>
 *
 * <h2>Reverse views</h2>
 * Two flat list endpoints aggregate slots across assignments to render
 * a weekly grid:
 * <ul>
 *   <li>{@code GET /v1/teachers/{teacherUuid}/schedule?periodId=...} —
 *       "what's my week".</li>
 *   <li>{@code GET /v1/academic/sections/{sectionUuid}/schedule?periodId=...} —
 *       "who teaches my class this week".</li>
 * </ul>
 *
 * <h2>Out of scope (deferred)</h2>
 * <ul>
 *   <li>Multi-classroom conflict detection — same physical room booked
 *       by two teachers at the same hour. Tracked as
 *       {@code DEBT-SCH-1} (P2, Sprint 6+ when classrooms become a
 *       first-class entity).</li>
 *   <li>Bulk import (XLSX) — single-row CRUD only for MVP. Tracked as
 *       {@code DEBT-SCH-2}.</li>
 *   <li>Different schedule per period within the same year — today the
 *       slots are tied to the assignment, which is already
 *       period-scoped, so this works for free.</li>
 * </ul>
 */
package com.edushift.modules.schedule.timeslot;
