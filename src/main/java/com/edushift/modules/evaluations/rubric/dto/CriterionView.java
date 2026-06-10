package com.edushift.modules.evaluations.rubric.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Output projection of a single criterion in a {@code Rubric}.
 *
 * <p>Mirrors the input shape ({@link CriterionInput}) without
 * validation annotations. Used in {@link RubricResponse} and
 * {@link RubricListItem} (the list view exposes the criterion names and
 * weights but trims the descriptors to keep the payload lean).</p>
 */
public record CriterionView(
		String key,
		String name,
		String description,
		BigDecimal weight,
		List<DescriptorView> descriptors
) {
}
