package com.edushift.modules.teachers.entity;

/**
 * Employment lifecycle for a {@link Teacher} inside a tenant.
 *
 * <h3>Transitions (informational only)</h3>
 * <pre>
 *   ACTIVE  ⇄  ON_LEAVE
 *   ACTIVE  →  RESIGNED  (terminal-ish: still readable for history)
 *   ACTIVE  →  RETIRED   (terminal-ish)
 *   ACTIVE  →  SUSPENDED (admin lockout; reversible to ACTIVE)
 * </pre>
 *
 * <h3>Operational impact</h3>
 * <ul>
 *   <li>Only {@link #ACTIVE} teachers may be assigned to new sections in
 *       BE-4.7 (teacher_assignments). Other states are admin-visible
 *       but hidden from the assignment dropdowns.</li>
 *   <li>{@link #SUSPENDED} blocks login when the linked user account is
 *       gated by enrollment status downstream — Sprint 9 will wire it.</li>
 * </ul>
 */
public enum EmploymentStatus {
	ACTIVE,
	ON_LEAVE,
	RESIGNED,
	RETIRED,
	SUSPENDED;

	public static EmploymentStatus fromName(String name) {
		if (name == null) return null;
		for (EmploymentStatus s : values()) {
			if (s.name().equals(name)) return s;
		}
		return null;
	}

	/** True if a teacher in this state can be assigned to new sections. */
	public boolean isAssignable() {
		return this == ACTIVE;
	}
}
