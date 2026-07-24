package com.edushift.modules.students.service;

import com.edushift.modules.students.dto.BulkImportJobResponse;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tenant-scoped façade for the bulk-import flow.
 *
 * <p>Originally introduced for students (Sprint 3); extended to
 * teachers in Sprint cierre-B / F7. The job row, lifecycle, and
 * tenant isolation are shared; the runner class and the parser vary
 * by target.</p>
 *
 * <h3>Workflow</h3>
 * <ol>
 *   <li>Admin uploads {@code .xlsx} via
 *       {@code POST /v1/students/bulk-import} or
 *       {@code POST /v1/teachers/bulk-import}.</li>
 *   <li>{@link #enqueueStudentsImport(MultipartFile)} /
 *       {@link #enqueueTeachersImport(MultipartFile)} validates the
 *       file envelope, creates a {@code PENDING} job row, and
 *       schedules the parsing in the background. Returns the
 *       (just-created) job to the caller — non-blocking.</li>
 *   <li>The worker parses the spreadsheet, validates each row, and
 *       persists the results, mutating the same job row as it goes.</li>
 *   <li>The UI polls {@link #getJob(UUID)} until {@code status} reaches
 *       a terminal state and renders the per-row error report.</li>
 * </ol>
 *
 * <h3>Authorisation</h3>
 * All methods require {@code TENANT_ADMIN}. The repository is
 * tenant-scoped automatically; admins from one tenant can never see
 * jobs from another (lookup returns 404).
 */
public interface BulkImportService {

	/**
	 * Validate the envelope of {@code file} and enqueue a student
	 * bulk-import job. Returns the {@code PENDING} job synchronously;
	 * parsing happens in the background.
	 */
	BulkImportJobResponse enqueueStudentsImport(MultipartFile file);

	/**
	 * Cierre-B / F7 — teacher counterpart of
	 * {@link #enqueueStudentsImport(MultipartFile)}. Same envelope
	 * validation; the worker is {@code TeacherBulkImportRunner}.
	 */
	BulkImportJobResponse enqueueTeachersImport(MultipartFile file);

	/**
	 * Look up a job by its public uuid. Throws
	 * {@link com.edushift.shared.exception.ResourceNotFoundException}
	 * when the id doesn't exist <em>or</em> belongs to another tenant.
	 */
	BulkImportJobResponse getJob(UUID publicUuid);

	/**
	 * Render the downloadable {@code .xlsx} template for the
	 * students import. Stateless / tenant-agnostic.
	 */
	byte[] generateStudentsTemplate();
}
