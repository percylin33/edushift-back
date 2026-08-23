package com.edushift.modules.schedule.daytemplate.dto;

import java.util.Map;

/**
 * Optional corrections map when committing a bootstrap draft.
 * Kept intentionally simple for v1.
 */
public record CommitBootstrapRequest(
		Map<String, String> corrections
) {
}
