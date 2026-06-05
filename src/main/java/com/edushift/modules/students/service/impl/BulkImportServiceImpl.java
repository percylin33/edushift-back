package com.edushift.modules.students.service.impl;

import com.edushift.modules.students.dto.BulkImportJobResponse;
import com.edushift.modules.students.entity.BulkImportJob;
import com.edushift.modules.students.entity.BulkImportJobType;
import com.edushift.modules.students.mapper.BulkImportJobMapper;
import com.edushift.modules.students.repository.BulkImportJobRepository;
import com.edushift.modules.students.service.BulkImportService;
import com.edushift.modules.students.service.bulk.StudentBulkImportRunner;
import com.edushift.modules.students.service.bulk.StudentTemplateGenerator;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Default {@link BulkImportService}.
 *
 * <h3>Where the work happens</h3>
 * This class only does the synchronous bookkeeping: it validates the
 * upload envelope, creates the {@code PENDING} job row, and hands the
 * payload off to {@link StudentBulkImportRunner#run(UUID, UUID, byte[])},
 * which is {@code @Async} on the {@code bulkImportExecutor} pool. The
 * caller therefore returns immediately with the new job's handle —
 * critical for big spreadsheets that would otherwise blow past the
 * HTTP timeout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkImportServiceImpl implements BulkImportService {

	/**
	 * Hard ceiling so a malicious or accidental upload can't consume
	 * the worker pool indefinitely. The application-level limit
	 * complements (does not replace) Spring's
	 * {@code spring.servlet.multipart.max-file-size}.
	 */
	private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("xlsx");

	private final BulkImportJobRepository jobRepository;
	private final BulkImportJobMapper mapper;
	private final StudentBulkImportRunner runner;
	private final StudentTemplateGenerator templateGenerator;

	// ===========================================================================
	// Upload + enqueue
	// ===========================================================================

	@Override
	@Transactional
	public BulkImportJobResponse enqueueStudentsImport(MultipartFile file) {
		validateUpload(file);

		byte[] payload;
		try {
			payload = file.getBytes();
		}
		catch (IOException e) {
			throw new BusinessException("INVALID_FILE",
					"Could not read the uploaded file: " + e.getMessage());
		}

		BulkImportJob job = new BulkImportJob();
		job.setJobType(BulkImportJobType.STUDENTS);
		job.setFileName(safeFileName(file));
		job.setFileSizeBytes(file.getSize());
		BulkImportJob saved = jobRepository.saveAndFlush(job);

		// Defer the @Async dispatch until AFTER the enclosing transaction
		// commits — otherwise the worker can race the commit and look up
		// the job via findById before the row is visible to other
		// connections, surfacing as a phantom 404 in the worker. If no
		// transaction is active (callers without @Transactional, e.g. unit
		// tests), fall back to dispatching synchronously — the worker is
		// still @Async and runs on its own pool.
		final UUID tenantId = TenantContext.currentRequired();
		final UUID jobId = saved.getId();
		final UUID jobPublicUuid = saved.getPublicUuid();
		final String fileName = saved.getFileName();
		final long fileSizeBytes = saved.getFileSizeBytes();
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(
					new TransactionSynchronization() {
						@Override
						public void afterCommit() {
							runner.run(jobId, tenantId, payload);
						}
					});
		}
		else {
			runner.run(jobId, tenantId, payload);
		}

		log.info("[bulk-import] enqueued -- jobId={} fileName={} size={} bytes",
				jobPublicUuid, fileName, fileSizeBytes);
		return mapper.toResponse(saved);
	}

	// ===========================================================================
	// Read
	// ===========================================================================

	@Override
	@Transactional(readOnly = true)
	public BulkImportJobResponse getJob(UUID publicUuid) {
		BulkImportJob job = jobRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("BulkImportJob", publicUuid));
		return mapper.toResponse(job);
	}

	@Override
	public byte[] generateStudentsTemplate() {
		return templateGenerator.generate();
	}

	// ===========================================================================
	// Internals
	// ===========================================================================

	private void validateUpload(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException("INVALID_FILE",
					"Upload must be a non-empty .xlsx file");
		}
		if (file.getSize() > MAX_FILE_BYTES) {
			throw new BusinessException("FILE_TOO_LARGE",
					"Upload exceeds the " + MAX_FILE_BYTES + " byte limit");
		}
		String extension = extensionOf(file.getOriginalFilename());
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new BusinessException("UNSUPPORTED_FILE_TYPE",
					"Only .xlsx uploads are accepted (got: ." + extension + ")");
		}
	}

	private static String extensionOf(String name) {
		if (name == null) return "";
		int idx = name.lastIndexOf('.');
		if (idx < 0 || idx == name.length() - 1) return "";
		return name.substring(idx + 1).toLowerCase(Locale.ROOT);
	}

	private static String safeFileName(MultipartFile file) {
		String original = file.getOriginalFilename();
		if (original == null || original.isBlank()) {
			return "upload.xlsx";
		}
		// Strip any leading path segments a browser may have included.
		int slash = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
		String trimmed = slash >= 0 ? original.substring(slash + 1) : original;
		return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
	}
}
