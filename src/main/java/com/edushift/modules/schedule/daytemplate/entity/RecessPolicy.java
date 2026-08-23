package com.edushift.modules.schedule.daytemplate.entity;

/**
 * Tenant recess coordination policy
 * ({@code tenant.settings.schedule.recessPolicy}, ADR-SCH-7).
 */
public enum RecessPolicy {
	/** Levels in the same share group keep identical recess windows. */
	SHARED,
	/** Each level (template) owns its own recess times (default). */
	STAGGERED
}
