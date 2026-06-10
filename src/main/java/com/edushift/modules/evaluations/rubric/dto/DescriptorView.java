package com.edushift.modules.evaluations.rubric.dto;

/**
 * Output projection of a single descriptor in a {@code Rubric}.
 *
 * <p>Mirrors {@link DescriptorInput} without validation annotations.</p>
 */
public record DescriptorView(
		String level,
		String text
) {
}
