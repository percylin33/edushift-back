package com.edushift.modules.academic.competency.entity;

/**
 * Who owns a {@link Competency} inside the shared course catalog.
 *
 * <ul>
 *   <li>{@link #MINEDU} — written by the on-demand curriculum seed.</li>
 *   <li>{@link #INSTITUTIONAL} — school catalog (admin create, or a
 *       teacher competency the admin promoted).</li>
 *   <li>{@link #TEACHER} — created by a teacher on a course they teach.
 *       {@code created_by} identifies the author.</li>
 * </ul>
 */
public enum CompetencyOrigin {
	MINEDU,
	INSTITUTIONAL,
	TEACHER
}
