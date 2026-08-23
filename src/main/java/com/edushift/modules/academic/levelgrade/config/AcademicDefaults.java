package com.edushift.modules.academic.levelgrade.config;

import com.edushift.modules.academic.levelgrade.entity.TeachingMode;
import java.util.List;

/**
 * Hard-coded defaults seeded into a new tenant on signup
 * (Sprint 4 / BE-4.2 + V103 teaching modes).
 */
public final class AcademicDefaults {

	private AcademicDefaults() {}

	public static final List<DefaultLevel> LEVELS = List.of(
			new DefaultLevel("INICIAL", "Inicial", 1, List.of(
					new DefaultGrade("3 años", 1, TeachingMode.MONODOCENTE),
					new DefaultGrade("4 años", 2, TeachingMode.MONODOCENTE),
					new DefaultGrade("5 años", 3, TeachingMode.MONODOCENTE)
			)),
			new DefaultLevel("PRIMARIA", "Primaria", 2, List.of(
					new DefaultGrade("1ro Primaria", 1, TeachingMode.MONODOCENTE),
					new DefaultGrade("2do Primaria", 2, TeachingMode.MONODOCENTE),
					new DefaultGrade("3ro Primaria", 3, TeachingMode.MONODOCENTE),
					new DefaultGrade("4to Primaria", 4, TeachingMode.MONODOCENTE),
					new DefaultGrade("5to Primaria", 5, TeachingMode.MIXTO),
					new DefaultGrade("6to Primaria", 6, TeachingMode.MIXTO)
			)),
			new DefaultLevel("SECUNDARIA", "Secundaria", 3, List.of(
					new DefaultGrade("1ro Secundaria", 1, TeachingMode.POLIDOCENTE),
					new DefaultGrade("2do Secundaria", 2, TeachingMode.POLIDOCENTE),
					new DefaultGrade("3ro Secundaria", 3, TeachingMode.POLIDOCENTE),
					new DefaultGrade("4to Secundaria", 4, TeachingMode.POLIDOCENTE),
					new DefaultGrade("5to Secundaria", 5, TeachingMode.POLIDOCENTE)
			))
	);

	public record DefaultLevel(String code, String name, int ordinal, List<DefaultGrade> grades) {
	}

	public record DefaultGrade(String name, int ordinal, TeachingMode teachingMode) {
	}
}
