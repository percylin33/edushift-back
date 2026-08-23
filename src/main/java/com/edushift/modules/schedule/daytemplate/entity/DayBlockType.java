package com.edushift.modules.schedule.daytemplate.entity;

/**
 * Block kinds inside a {@link DayScheduleTemplate}.
 *
 * <p>{@link #RECESS}, {@link #LUNCH} and {@link #ASSEMBLY} are hard
 * non-teaching windows (no TimeSlot / LearningSession). {@link #GUIDANCE}
 * and {@link #SPECIALIST_RESERVED} are soft / advisory.</p>
 */
public enum DayBlockType {
	RECESS,
	LUNCH,
	ASSEMBLY,
	GUIDANCE,
	SPECIALIST_RESERVED;

	/** True for windows that must never host teaching slots. */
	public boolean isHardNonTeaching() {
		return this == RECESS || this == LUNCH || this == ASSEMBLY;
	}
}
