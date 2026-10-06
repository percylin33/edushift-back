package com.edushift.modules.students.service;

import com.edushift.modules.students.dto.BulkImportJobResponse;
import com.edushift.modules.students.dto.BulkImportPreviewResponse;
import com.edushift.modules.students.service.bulk.StudentBulkImportOptions;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tenant-scoped façade for the bulk-import flow.
 */
public interface BulkImportService {

	/**
	 * Preview spreadsheet headers and suggested column mapping without
	 * creating a job.
	 */
	BulkImportPreviewResponse previewStudentsImport(MultipartFile file);

	/**
	 * Validate the envelope of {@code file} and enqueue a student
	 * bulk-import job. Returns the {@code PENDING} job synchronously;
	 * parsing happens in the background.
	 */
	BulkImportJobResponse enqueueStudentsImport(MultipartFile file);

	/**
	 * Same as {@link #enqueueStudentsImport(MultipartFile)} with optional
	 * column mapping and destination section enrollment.
	 */
	BulkImportJobResponse enqueueStudentsImport(MultipartFile file,
			StudentBulkImportOptions options);

	/**
	 * Cierre-B / F7 — teacher counterpart of
	 * {@link #enqueueStudentsImport(MultipartFile)}.
	 */
	BulkImportJobResponse enqueueTeachersImport(MultipartFile file);

	/**
	 * Teacher import with optional column mapping
	 * ({@code canonical → exact Excel header}).
	 */
	BulkImportJobResponse enqueueTeachersImport(MultipartFile file,
			java.util.Map<String, String> columnMapping);

	/**
	 * Preview teacher spreadsheet headers and suggested mapping.
	 */
	BulkImportPreviewResponse previewTeachersImport(MultipartFile file);

	/**
	 * Look up a job by its public uuid.
	 */
	BulkImportJobResponse getJob(UUID publicUuid);

	/**
	 * Render the downloadable {@code .xlsx} template for the
	 * students import. Stateless / tenant-agnostic.
	 */
	byte[] generateStudentsTemplate();

	/**
	 * Render the downloadable {@code .xlsx} template for the
	 * teachers import. Stateless / tenant-agnostic.
	 */
	byte[] generateTeachersTemplate();

	/**
	 * Parse the multipart {@code columnMapping} JSON part into
	 * {@code canonical → exactHeader}. Returns {@code null} when blank.
	 */
	java.util.Map<String, String> parseColumnMappingJson(String json);
}
