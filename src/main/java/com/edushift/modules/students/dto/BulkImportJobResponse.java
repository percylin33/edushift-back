package com.edushift.modules.students.dto;

import com.edushift.modules.students.entity.BulkImportJob;
import com.edushift.modules.students.entity.BulkImportJobType;
import com.edushift.modules.students.entity.BulkImportStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public representation of a {@link BulkImportJob} (its handle,
 * lifecycle, counters, and per-row errors).
 *
 * <p>Returned from both:
 * <ul>
 *   <li>{@code POST /v1/students/bulk-import} — newly enqueued job, with
 *       {@code status=PENDING} and counters at zero.</li>
 *   <li>{@code GET /v1/students/bulk-import/{publicUuid}} — current
 *       progress / final result, used by the UI to drive the progress
 *       bar.</li>
 * </ul>
 */
public record BulkImportJobResponse(
		UUID publicUuid,
		BulkImportJobType jobType,
		BulkImportStatus status,
		String fileName,
		long fileSizeBytes,
		Integer totalRows,
		int processedRows,
		int errorRows,
		List<BulkImportJob.RowError> errors,
		String failReason,
		Instant startedAt,
		Instant finishedAt,
		Instant createdAt
) {
}
