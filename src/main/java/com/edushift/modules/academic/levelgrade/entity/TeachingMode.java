package com.edushift.modules.academic.levelgrade.entity;

/**
 * How teachers are assigned to sections of a {@link Grade}
 * (V103 / ADR-SCH-8).
 *
 * <ul>
 *   <li>{@link #MONODOCENTE} — one primary teacher owns most of the day;
 *       specialists travel into reserved windows.</li>
 *   <li>{@link #POLIDOCENTE} — multiple subject teachers; usually needs a
 *       homeroom tutor.</li>
 *   <li>{@link #MIXTO} — hybrid (e.g. lower primaria mono + specialists).</li>
 * </ul>
 */
public enum TeachingMode {
	MONODOCENTE,
	POLIDOCENTE,
	MIXTO
}
