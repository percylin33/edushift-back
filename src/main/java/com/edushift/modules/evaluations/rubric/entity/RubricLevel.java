package com.edushift.modules.evaluations.rubric.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Canonical MINEDU achievement levels for a {@code Rubric}. Sprint 5B /
 * BE-5B.2.
 *
 * <p>These are the four reference levels published by Peru's Ministerio
 * de Educacion (MINEDU) Curriculo Nacional (CNEB). Tenants can use a
 * subset (2..4) but the codes themselves are reserved and the
 * service layer enforces uniqueness within a rubric's
 * {@code levels[]} array.</p>
 *
 * <h3>Ordering</h3>
 * The enum's {@link #order()} reflects the pedagogical progression
 * from low to high. {@link #fromCode(String)} is case-insensitive and
 * tolerant of legacy tenant data (trims whitespace).
 */
public enum RubricLevel {

	EN_INICIO(1, "En inicio", "C-", "El estudiante muestra un logro proximo a lo esperado."),
	EN_PROCESO(2, "En proceso", "B", "El estudiante muestra un logro en proceso hacia lo esperado."),
	ESPERADO(3, "Esperado", "A", "El estudiante evidencia el logro de los aprendizajes esperados."),
	SOBRESALIENTE(4, "Sobresaliente", "AD", "El estudiante evidencia un logro sobresaliente.");

	private final int order;
	private final String displayName;
	private final String shortCode;
	private final String description;

	RubricLevel(int order, String displayName, String shortCode, String description) {
		this.order = order;
		this.displayName = displayName;
		this.shortCode = shortCode;
		this.description = description;
	}

	public int order() {
		return order;
	}

	public String displayName() {
		return displayName;
	}

	public String shortCode() {
		return shortCode;
	}

	public String description() {
		return description;
	}

	/**
	 * Resolves a level code to its enum constant, ignoring case and
	 * surrounding whitespace. Returns {@link Optional#empty()} when the
	 * code is not one of the canonical four.
	 */
	public static Optional<RubricLevel> fromCode(String code) {
		if (code == null) return Optional.empty();
		String normalized = code.trim();
		if (normalized.isEmpty()) return Optional.empty();
		return Arrays.stream(values())
				.filter(l -> l.name().equalsIgnoreCase(normalized))
				.findFirst();
	}

	/**
	 * Returns true if {@code code} is one of the canonical level codes
	 * (case-insensitive). Used by the service to detect when a
	 * tenant-defined rubric is also using the MINEDU reference levels.
	 */
	public static boolean isCanonical(String code) {
		return fromCode(code).isPresent();
	}
}
