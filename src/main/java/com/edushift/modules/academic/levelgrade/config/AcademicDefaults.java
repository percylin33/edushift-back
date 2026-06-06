package com.edushift.modules.academic.levelgrade.config;

import java.util.List;

/**
 * Hard-coded defaults seeded into a new tenant on signup
 * (Sprint 4 / BE-4.2).
 *
 * <p>Hard-coded instead of {@code @ConfigurationProperties} for simplicity
 * — Spring's properties binding for nested lists in {@code .properties}
 * format is verbose, and this catalog rarely changes. If a future
 * customer needs per-environment overrides we can migrate to YAML
 * config (tracked as DEBT-ACAD-1 in {@code docs/product/tech-debt.md}).</p>
 *
 * <h3>Source of truth</h3>
 * Levels: INICIAL / PRIMARIA / SECUNDARIA — matches the Peruvian K-12
 * curriculum. A Cambridge-style school wanting {@code IGCSE} or
 * {@code IB_DIPLOMA} simply adds them through the API after signup; the
 * defaults below remain untouched.
 */
public final class AcademicDefaults {

	private AcademicDefaults() {}

	public static final List<DefaultLevel> LEVELS = List.of(
			new DefaultLevel("INICIAL", "Inicial", 1, List.of(
					new DefaultGrade("3 años", 1),
					new DefaultGrade("4 años", 2),
					new DefaultGrade("5 años", 3)
			)),
			new DefaultLevel("PRIMARIA", "Primaria", 2, List.of(
					new DefaultGrade("1ro Primaria", 1),
					new DefaultGrade("2do Primaria", 2),
					new DefaultGrade("3ro Primaria", 3),
					new DefaultGrade("4to Primaria", 4),
					new DefaultGrade("5to Primaria", 5),
					new DefaultGrade("6to Primaria", 6)
			)),
			new DefaultLevel("SECUNDARIA", "Secundaria", 3, List.of(
					new DefaultGrade("1ro Secundaria", 1),
					new DefaultGrade("2do Secundaria", 2),
					new DefaultGrade("3ro Secundaria", 3),
					new DefaultGrade("4to Secundaria", 4),
					new DefaultGrade("5to Secundaria", 5)
			))
	);

	public record DefaultLevel(String code, String name, int ordinal, List<DefaultGrade> grades) {
	}

	public record DefaultGrade(String name, int ordinal) {
	}
}
