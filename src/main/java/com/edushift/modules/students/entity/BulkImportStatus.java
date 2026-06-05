package com.edushift.modules.students.entity;

/**
 * Lifecycle states of a {@link BulkImportJob}.
 *
 * <pre>
 *   PENDING -- worker enqueued -->  PROCESSING -- finished --> COMPLETED
 *      \-- aborted ---------> FAILED              \-- aborted -> FAILED
 * </pre>
 *
 * <p>{@code COMPLETED} can still carry per-row failures inside
 * {@code errorsJson}; what makes it "completed" is that the parser
 * walked every row in the spreadsheet, not that every row was
 * successfully persisted. {@code FAILED} signals an aborting failure
 * the worker couldn't recover from (invalid file format, IO error,
 * transient DB outage), reported via {@code failReason}.
 */
public enum BulkImportStatus {

	PENDING,
	PROCESSING,
	COMPLETED,
	FAILED;

	public boolean isTerminal() {
		return this == COMPLETED || this == FAILED;
	}
}
