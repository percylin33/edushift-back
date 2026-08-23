/**
 * Day schedule templates — school-day structure (recess / lunch / assembly)
 * scoped to {@code AcademicYear} + {@code AcademicLevel} with optional
 * {@code Grade} override (ADR-SCH-6..11).
 *
 * <p>TimeSlots remain teaching-only; hard non-teaching windows live here
 * and are enforced via {@code NonTeachingBlockResolver}.</p>
 */
package com.edushift.modules.schedule.daytemplate;
