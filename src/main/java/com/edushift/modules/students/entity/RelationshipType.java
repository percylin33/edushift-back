package com.edushift.modules.students.entity;

/**
 * Nature of the relationship between a {@link Guardian} and a {@link Student}.
 *
 * <p>Lives on the {@link StudentGuardian} link, not on the guardian
 * itself, because the same person can have different relationships
 * with different students (e.g. {@code MOTHER} of one and
 * {@code GUARDIAN} of another). {@code OTHER} is the escape hatch for
 * situations the closed list doesn't cover (uncle, aunt, foster
 * parent in a non-standard arrangement). Anything more nuanced
 * belongs in a free-text "notes" column we haven't introduced yet.
 */
public enum RelationshipType {
	FATHER,
	MOTHER,
	GRANDPARENT,
	GUARDIAN,
	OTHER;

	public static RelationshipType fromName(String name) {
		if (name == null) return null;
		for (RelationshipType r : values()) {
			if (r.name().equals(name)) return r;
		}
		return null;
	}
}
