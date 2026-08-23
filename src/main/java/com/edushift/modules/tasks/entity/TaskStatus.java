package com.edushift.modules.tasks.entity;

/**
 * Task lifecycle (BE-7a.2 + BUG-2026-07-31-04).
 *
 * <h3>States</h3>
 * <ul>
 *   <li>{@link #DRAFT} — created, editable, NOT visible to students.</li>
 *   <li>{@link #PUBLISHED} — frozen, visible to students in the section,
 *       accepts submissions until {@code dueAt}.</li>
 *   <li>{@link #ARCHIVED} — closed, read-only history. Reachable
 *       either manually (teacher archives) or automatically
 *       (post-dueAt sweep, future).</li>
 * </ul>
 *
 * <p>Persisted as {@code VARCHAR(16)} via {@code @Enumerated(STRING)}.
 * DB-level valid values enforced by
 * {@code chk_lms_tasks_status} in {@code V95__add_lms_tasks_status.sql}.
 */
public enum TaskStatus {
	DRAFT,
	PUBLISHED,
	ARCHIVED
}
