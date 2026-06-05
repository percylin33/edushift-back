package com.edushift.modules.students.service.bulk;

import com.edushift.modules.students.dto.CreateStudentRequest;
import com.edushift.modules.students.entity.BulkImportJob;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.repository.BulkImportJobRepository;
import com.edushift.modules.students.service.StudentService;
import com.edushift.shared.exception.ApiException;
import com.edushift.shared.multitenancy.TenantContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worker for student bulk-import jobs. Lives in its own component so
 * the service-layer code that schedules jobs (creates the row, returns
 * 202) stays trivial.
 *
 * <h3>Why @Async on a separate bean</h3>
 * Spring's AOP proxy for {@code @Async} only kicks in on calls that
 * cross a Spring bean boundary. Putting the worker on a dedicated bean
 * makes the cross-boundary call obvious to the reader and keeps the
 * service free of self-invocation footguns.
 *
 * <h3>Tenant context</h3>
 * The {@link com.edushift.infrastructure.async.ContextPropagatingTaskDecorator}
 * configured on {@code bulkImportExecutor} carries the original
 * tenant id into the worker thread. As a defence in depth the runner
 * re-asserts it explicitly via {@link TenantContext#runAs}, since
 * downstream code (Hibernate {@code @TenantId}, audit, logs) all keys
 * off the same {@code TenantContext}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentBulkImportRunner {

	private final StudentBulkImportParser parser;
	private final StudentService studentService;
	private final BulkImportJobRepository jobRepository;
	private final Validator validator;

	/**
	 * Process a job in the background.
	 *
	 * <p>This method runs on the {@code bulkImportExecutor} pool. The
	 * caller (controller / service that creates the {@code PENDING}
	 * row) returns immediately with the new job's handle.
	 *
	 * @param jobId    internal id of the {@code PENDING} job to process
	 * @param tenantId tenant the job belongs to (re-asserted into the
	 *                 worker's {@link TenantContext})
	 * @param payload  raw spreadsheet bytes (xlsx). Stored in memory so
	 *                 the worker doesn't need disk access; capped by
	 *                 the controller's multipart size limit.
	 */
	@Async("bulkImportExecutor")
	public void run(UUID jobId, UUID tenantId, byte[] payload) {
		TenantContext.runAs(tenantId, () -> {
			processJob(jobId, payload);
			return null;
		});
	}

	@Transactional
	void processJob(UUID jobId, byte[] payload) {
		BulkImportJob job = jobRepository.findById(jobId).orElse(null);
		if (job == null) {
			log.warn("[bulk-import] job not found at runner start -- jobId={}", jobId);
			return;
		}
		job.markStarted();
		jobRepository.saveAndFlush(job);

		List<StudentRowDraft> drafts;
		try {
			drafts = parser.parse(new ByteArrayInputStream(payload));
		}
		catch (BulkImportException e) {
			log.warn("[bulk-import] parse failed -- jobId={} code={} msg={}",
					jobId, e.getCode(), e.getMessage());
			job.markFailed(e.getMessage());
			jobRepository.saveAndFlush(job);
			return;
		}

		job.setTotalRows(drafts.size());
		jobRepository.saveAndFlush(job);

		Set<String> documentsInBatch = new HashSet<>();
		Set<String> emailsInBatch = new HashSet<>();
		for (StudentRowDraft draft : drafts) {
			processOneRow(job, draft, documentsInBatch, emailsInBatch);
			job.incrementProcessed();
		}

		job.markCompleted();
		jobRepository.saveAndFlush(job);
		log.info("[bulk-import] completed -- jobId={} total={} errors={}",
				jobId, job.getTotalRows(), job.getErrorRows());
	}

	// ===========================================================================
	// Per-row work
	// ===========================================================================

	private void processOneRow(BulkImportJob job, StudentRowDraft draft,
			Set<String> documentsInBatch, Set<String> emailsInBatch) {
		// 1. Sanity-check the parsed shape.
		List<String> coercionErrors = collectCoercionErrors(draft);
		if (!coercionErrors.isEmpty()) {
			job.recordRowError(draft.rowNumber(), "ROW_INVALID",
					String.join("; ", coercionErrors));
			return;
		}

		// 2. Local in-batch duplicate detection — gives us a clean
		// `ROW_DUPLICATE` instead of letting the second insert blow up
		// on the unique index, which would surface as a generic 409.
		String docKey = draft.documentType().name() + ":" + draft.documentNumber();
		if (!documentsInBatch.add(docKey)) {
			job.recordRowError(draft.rowNumber(), "ROW_DUPLICATE",
					"Duplicate document " + draft.documentType()
							+ " " + draft.documentNumber() + " inside the same upload");
			return;
		}
		String email = normaliseEmail(draft.email());
		if (email != null && !emailsInBatch.add(email)) {
			job.recordRowError(draft.rowNumber(), "ROW_DUPLICATE",
					"Duplicate email " + email + " inside the same upload");
			return;
		}

		// 3. Map to the same DTO shape the public POST uses, then run
		// the same Bean Validation pass so we catch length / pattern
		// mistakes uniformly.
		CreateStudentRequest request = toCreateRequest(draft);
		Set<ConstraintViolation<CreateStudentRequest>> violations = validator.validate(request);
		if (!violations.isEmpty()) {
			String message = violations.stream()
					.map(v -> v.getPropertyPath() + ": " + v.getMessage())
					.collect(Collectors.joining("; "));
			job.recordRowError(draft.rowNumber(), "ROW_INVALID", message);
			return;
		}

		// 4. Persist via the public service so audit, normalisation,
		// and uniqueness conflicts behave identically to the manual
		// POST path.
		try {
			studentService.createStudent(request);
		}
		catch (ApiException e) {
			job.recordRowError(draft.rowNumber(), e.getCode(), e.getMessage());
		}
		catch (RuntimeException e) {
			job.recordRowError(draft.rowNumber(), "ROW_INVALID",
					e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
		}
	}

	// ---------------------------------------------------------------------------
	// Coercion + mapping
	// ---------------------------------------------------------------------------

	private List<String> collectCoercionErrors(StudentRowDraft draft) {
		List<String> errors = new java.util.ArrayList<>();
		if (draft.documentType() == null) {
			errors.add("documentType is required and must be one of: "
					+ enumValues(DocumentType.class));
		}
		if (draft.documentNumber() == null || draft.documentNumber().isBlank()) {
			errors.add("documentNumber is required");
		}
		if (draft.firstName() == null || draft.firstName().isBlank()) {
			errors.add("firstName is required");
		}
		if (draft.lastName() == null || draft.lastName().isBlank()) {
			errors.add("lastName is required");
		}
		// Optional enum fields: when present but unparsable they were
		// coerced to null; the sentinel here is "the column had a
		// value but it didn't match an enum constant". We can't tell
		// the difference cheaply once they're null, so we accept the
		// tradeoff: missing optional enums silently default to entity
		// defaults, just like the public POST endpoint does.
		return errors;
	}

	private CreateStudentRequest toCreateRequest(StudentRowDraft d) {
		return new CreateStudentRequest(
				d.documentType(),
				d.documentNumber(),
				d.firstName(),
				d.lastName(),
				d.secondLastName(),
				d.birthDate(),
				d.gender() == null ? Gender.NOT_SPECIFIED : d.gender(),
				d.email(),
				d.phone(),
				d.address(),
				d.enrollmentStatus() == null ? EnrollmentStatus.PENDING : d.enrollmentStatus(),
				d.enrollmentDate(),
				null
		);
	}

	private static String normaliseEmail(String raw) {
		if (raw == null) return null;
		String trimmed = raw.trim();
		return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
	}

	private static <E extends Enum<E>> String enumValues(Class<E> type) {
		StringBuilder sb = new StringBuilder();
		for (E v : type.getEnumConstants()) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(v.name());
		}
		return sb.toString();
	}
}
