package com.edushift.modules.students.dto;

import java.util.List;
import java.util.Map;

/**
 * Result of {@code POST /v1/students/bulk-import/preview}.
 *
 * <p>Does not persist anything — the FE uses this to drive the column
 * mapping step before enqueueing the real import job.
 *
 * @param headers            exact header labels from sheet 0 (row 0)
 * @param sampleRows         first N data rows as {@code header → cell}
 * @param suggestedMapping   {@code canonicalField → exact header}
 * @param unmappedRequired   canonical student-required fields still missing
 */
public record BulkImportPreviewResponse(
		List<String> headers,
		List<Map<String, String>> sampleRows,
		Map<String, String> suggestedMapping,
		List<String> unmappedRequired
) {
}
