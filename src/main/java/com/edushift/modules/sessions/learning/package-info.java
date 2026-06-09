/**
 * Sessions - daily occurrence of a {@code TeacherAssignment + Unit}
 * on a {@code scheduled_date} (Sprint 5A / BE-5A.4).
 *
 * <h2>Purpose</h2>
 * <p>A {@code LearningSession} is the concrete materialisation of one
 * day of class. It anchors:</p>
 * <ul>
 *   <li>The {@code TeacherAssignment} (Sprint 4 / BE-4.7), which fixes
 *       teacher + section + course + period.</li>
 *   <li>The {@code Unit} (BE-5A.1), which must belong to the same
 *       {@code course} as the assignment.</li>
 *   <li>Zero or more {@code Competency} + {@code Capacity} records
 *       (BE-5A.2), all of which must belong to the same course.</li>
 *   <li>A free-form {@code content} JSON blob with
 *       {@code (objective, activities[], materials[], observations)}.
 *       Stored as Postgres {@code jsonb} via Hibernate's
 *       {@code @JdbcTypeCode(SqlTypes.JSON)}.</li>
 * </ul>
 *
 * <h2>Lifecycle (state machine)</h2>
 * <pre>{@code
 *      PLANNED ----start----> IN_PROGRESS ----complete----> COMPLETED
 *         |                       |
 *         +----- cancel ----------+----> CANCELLED
 * }</pre>
 *
 * <ul>
 *   <li>{@code COMPLETED} and {@code CANCELLED} are terminal: every
 *       outgoing transition returns 409 {@code SESSION_TRANSITION_INVALID}.</li>
 *   <li>{@code start()} stamps {@code started_at}; {@code complete()}
 *       stamps {@code ended_at}; {@code cancel()} stamps
 *       {@code cancelled_at}. Coherence is enforced by both Bean
 *       Validation rules in the service AND a Postgres CHECK
 *       constraint (defence in depth).</li>
 *   <li>{@code @Version} optimistic lock prevents a race where two
 *       admins both click "Start" on the same PLANNED row.</li>
 * </ul>
 *
 * <h2>Cross-context invariants</h2>
 * <ul>
 *   <li><strong>Date inside period</strong> - {@code scheduled_date}
 *       must lie within the assignment's
 *       {@code [period.startDate, period.endDate]} window. Violations
 *       are 400 {@code SESSION_DATE_OUT_OF_PERIOD}.</li>
 *   <li><strong>Unit in course</strong> - the chosen unit's course
 *       must equal the assignment's course. 400
 *       {@code UNIT_NOT_IN_COURSE}.</li>
 *   <li><strong>Competencies in course</strong> - each competency
 *       must belong to the assignment's course. 400
 *       {@code COMPETENCY_NOT_IN_COURSE}.</li>
 *   <li><strong>Capacities in course</strong> - each capacity's
 *       parent competency must belong to the assignment's course. 400
 *       {@code CAPACITY_NOT_IN_COURSE}.</li>
 *   <li><strong>Active assignment</strong> - the session's parent
 *       assignment must not be soft-ended at create / update time.
 *       409 {@code ASSIGNMENT_NOT_ACTIVE}.</li>
 * </ul>
 *
 * <h2>Reverse views</h2>
 * <ul>
 *   <li>{@code GET /v1/teacher-assignments/{a}/sessions} - all
 *       sessions of an assignment ordered by date.</li>
 *   <li>{@code GET /v1/academic/units/{u}/sessions} - all sessions
 *       referencing a unit, used by the unit-detail UI to render
 *       the "lessons in this unit" list.</li>
 * </ul>
 *
 * <h2>Out of scope (deferred)</h2>
 * <ul>
 *   <li><strong>AI generation</strong> - "scaffold a session given
 *       unit + competencies" is Sprint 8 (module {@code ai/}).</li>
 *   <li><strong>Attendance integration</strong> - {@code SESSION_HAS_ATTENDANCE}
 *       is a placeholder error code; Sprint 6 will wire it to the
 *       attendance module via a domain event.</li>
 *   <li><strong>Recurrence with exceptions</strong> - "skip Tuesday,
 *       move to Friday" is the responsibility of the FE flow, which
 *       deletes one session and creates another.</li>
 * </ul>
 */
package com.edushift.modules.sessions.learning;
