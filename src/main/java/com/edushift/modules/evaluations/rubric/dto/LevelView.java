package com.edushift.modules.evaluations.rubric.dto;

/**
 * Output projection of a single achievement level in a {@code Rubric}.
 *
 * <p>Mirrors {@link LevelInput}. The {@code order} field is optional —
 * if absent in the source, the service fills it from the list position.</p>
 */
public record LevelView(
		String code,
		String name,
		Integer order
) {
}
