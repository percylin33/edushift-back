package com.edushift.modules.evaluations.graderegister.entity;

/**
 * How columns under a {@link GradeCategory} are structured on the planilla.
 *
 * <ul>
 *   <li>{@link #OPEN} — teacher creates evaluations freely (F1 default).</li>
 *   <li>{@link #SERIES} — N named slots (e.g. S1…S10) materialised on gradebook open.</li>
 *   <li>{@link #SINGLE} — exactly one fixed cell (e.g. Conducta).</li>
 * </ul>
 */
public enum GradeCategoryColumnMode {
	OPEN,
	SERIES,
	SINGLE
}
