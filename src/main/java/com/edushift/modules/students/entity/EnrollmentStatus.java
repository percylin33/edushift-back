package com.edushift.modules.students.entity;

/**
 * Lifecycle of a {@link Student} <em>inside the institution</em>.
 *
 * <p>Distinct from {@code deleted} (administrative removal) and from any
 * future "academic year" cycle. A student may be {@code ENROLLED} in a
 * tenant for years across multiple academic years; the status moves to
 * {@code GRADUATED} when they complete the cycle, {@code TRANSFERRED}
 * when they move to another institution, and {@code WITHDRAWN} when
 * they leave for any other reason. {@code PENDING} is the default
 * post-creation state, before formal enrollment paperwork is finished.
 */
public enum EnrollmentStatus {
	PENDING,
	ENROLLED,
	GRADUATED,
	TRANSFERRED,
	WITHDRAWN;

	public static EnrollmentStatus fromName(String name) {
		if (name == null) return null;
		for (EnrollmentStatus s : values()) {
			if (s.name().equals(name)) return s;
		}
		return null;
	}
}
