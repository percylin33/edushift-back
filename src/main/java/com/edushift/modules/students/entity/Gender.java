package com.edushift.modules.students.entity;

/**
 * Self-reported gender for a {@link Student}.
 *
 * <p>{@code NOT_SPECIFIED} is the default and the value we land on when the
 * institution either does not collect this datum or the family declined
 * to share it. Treat this as administrative metadata only — the platform
 * never gates features on gender.
 */
public enum Gender {
	MALE,
	FEMALE,
	OTHER,
	NOT_SPECIFIED;

	public static Gender fromName(String name) {
		if (name == null) return null;
		for (Gender g : values()) {
			if (g.name().equals(name)) return g;
		}
		return null;
	}
}
