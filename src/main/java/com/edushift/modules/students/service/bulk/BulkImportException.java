package com.edushift.modules.students.service.bulk;

/**
 * Aborting failure during bulk import — invalid spreadsheet shape,
 * unsupported file type, IO error reading the upload. The runner
 * catches this on the worker thread, marks the job {@code FAILED}, and
 * stops parsing. Per-row failures are recorded as
 * {@code RowError} entries on the job, not by throwing this.
 */
public class BulkImportException extends RuntimeException {

	private final String code;

	public BulkImportException(String code, String message) {
		super(message);
		this.code = code;
	}

	public BulkImportException(String code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
