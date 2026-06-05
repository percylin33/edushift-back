package com.edushift.modules.students.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Tracks the lifecycle of an asynchronous bulk-import job (Excel/CSV
 * upload).
 *
 * <h3>Why a database row and not just a queue message</h3>
 * The UI must be able to poll progress ({@code processedRows / totalRows})
 * and to render per-row errors after the fact, without relying on the
 * worker still being alive. A persisted row gives us:
 * <ul>
 *   <li>A handle ({@code publicUuid}) the client can poll repeatedly.</li>
 *   <li>Survives application restarts — half-done jobs surface as
 *       {@code PROCESSING} and an admin can decide what to do.</li>
 *   <li>Per-tenant audit / billing visibility for free.</li>
 * </ul>
 *
 * <h3>{@code errorsJson}</h3>
 * Stores per-row failures as a JSON array
 * (e.g. {@code [{"row":3,"code":"ROW_INVALID","message":"…"}]}).
 * Capping at jsonb keeps it queryable; modeling each error as a sibling
 * table is overkill for a feature whose audience is admins, not
 * analytics consumers.
 */
@Entity
@Table(
		name = "bulk_import_jobs",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_bulk_import_jobs_public_uuid",
						columnNames = "public_uuid")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"publicUuid", "jobType", "status", "fileName"})
public class BulkImportJob extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@Enumerated(EnumType.STRING)
	@Column(name = "job_type", nullable = false, length = 40)
	private BulkImportJobType jobType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private BulkImportStatus status = BulkImportStatus.PENDING;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	@Column(name = "file_size_bytes", nullable = false)
	private long fileSizeBytes;

	@Column(name = "total_rows")
	private Integer totalRows;

	@Column(name = "processed_rows", nullable = false)
	private int processedRows;

	@Column(name = "error_rows", nullable = false)
	private int errorRows;

	/**
	 * Per-row errors as a JSON array. The list is owned by the entity;
	 * the worker mutates it and JPA flushes it back to {@code jsonb}.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "errors_json", nullable = false, columnDefinition = "jsonb")
	private List<RowError> errors = new ArrayList<>();

	@Column(name = "fail_reason", length = 500)
	private String failReason;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (status == null) {
			status = BulkImportStatus.PENDING;
		}
		if (errors == null) {
			errors = new ArrayList<>();
		}
	}

	// ---------------------------------------------------------------------------
	// Behaviour
	// ---------------------------------------------------------------------------

	public void markStarted() {
		this.status = BulkImportStatus.PROCESSING;
		this.startedAt = Instant.now();
	}

	public void markCompleted() {
		this.status = BulkImportStatus.COMPLETED;
		this.finishedAt = Instant.now();
	}

	public void markFailed(String reason) {
		this.status = BulkImportStatus.FAILED;
		this.finishedAt = Instant.now();
		this.failReason = truncate(reason, 500);
	}

	public void incrementProcessed() {
		this.processedRows++;
	}

	public void recordRowError(int rowNumber, String code, String message) {
		if (errors == null) {
			errors = new ArrayList<>();
		}
		errors.add(new RowError(rowNumber, code, truncate(message, 500)));
		this.errorRows++;
	}

	public List<RowError> getErrorsView() {
		return errors == null
				? Collections.emptyList()
				: Collections.unmodifiableList(errors);
	}

	private static String truncate(String s, int max) {
		if (s == null) return null;
		return s.length() <= max ? s : s.substring(0, max);
	}

	// ---------------------------------------------------------------------------
	// Embedded value type
	// ---------------------------------------------------------------------------

	/**
	 * One row failure inside {@code errorsJson}. Serialised by Jackson
	 * into the jsonb column.
	 *
	 * <p>{@code row} is the 1-based row number as the spreadsheet shows
	 * it (header row = 1, first data row = 2). Admins reading the error
	 * report will be able to navigate to the offending row directly.
	 */
	public record RowError(int row, String code, String message) {
	}
}
