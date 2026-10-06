package com.edushift.modules.academic.course;

/**
 * Suggested {@code icon} + {@code color} for a course {@code code}.
 * Used when create payload omits visual fields and as FE suggestion mirror.
 */
public final class CourseVisualDefaults {

	public static final String DEFAULT_ICON = "book-open";
	public static final String DEFAULT_COLOR = "#64748B";

	private CourseVisualDefaults() {
	}

	public record Visual(String icon, String color) {
	}

	public static Visual fromCode(String code) {
		if (code == null || code.isBlank()) {
			return new Visual(DEFAULT_ICON, DEFAULT_COLOR);
		}
		String normalized = code.trim().toUpperCase();

		// Longer codes first so COMP is not treated as COM and PSI is not PS.
		if (matches(normalized, "COMP", "PROG")) {
			return new Visual("cpu", "#22D3EE");
		}
		if (matches(normalized, "COMU") || exact(normalized, "COM")) {
			return new Visual("book-open", "#DC2626");
		}
		if (matches(normalized, "PSI")) {
			return new Visual("user", "#84CC16");
		}
		if (exact(normalized, "PS")) {
			return new Visual("users", "#F97316");
		}
		if (matches(normalized, "CYT", "CCNN", "CIE")) {
			return new Visual("sparkles", "#16A34A");
		}
		if (matches(normalized, "CCSS", "HIS", "GEO")) {
			return new Visual("globe", "#F97316");
		}
		if (matches(normalized, "DPCC")) {
			return new Visual("award", "#0F766E");
		}
		if (matches(normalized, "EPT")) {
			return new Visual("clipboard-list", "#B45309");
		}
		if (matches(normalized, "ING")) {
			return new Visual("languages", "#EAB308");
		}
		if (exact(normalized, "EF")) {
			return new Visual("dumbbell", "#DB2777");
		}
		if (exact(normalized, "ER") || matches(normalized, "REL")) {
			return new Visual("book", "#FFFFFF");
		}
		if (matches(normalized, "TUT")) {
			return new Visual("users", "#6366F1");
		}
		if (exact(normalized, "AC") || matches(normalized, "ART")) {
			return new Visual("palette", "#7C3AED");
		}
		if (normalized.startsWith("MAT")) {
			return new Visual("target", "#2563EB");
		}
		return new Visual(DEFAULT_ICON, DEFAULT_COLOR);
	}

	/** Exact code, {@code KEY-…}, {@code KEY_…}, or a longer code that starts with a key of length ≥ 3. */
	private static boolean matches(String code, String... keys) {
		for (String key : keys) {
			if (exact(code, key)) {
				return true;
			}
			if (key.length() >= 3 && code.startsWith(key) && !code.startsWith(key + "_")) {
				return true;
			}
		}
		return false;
	}

	private static boolean exact(String code, String key) {
		return code.equals(key) || code.startsWith(key + "-");
	}
}
