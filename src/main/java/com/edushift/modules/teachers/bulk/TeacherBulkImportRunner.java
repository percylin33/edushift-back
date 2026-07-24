package com.edushift.modules.teachers.bulk;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.service.bulk.BulkImportException;
import com.edushift.modules.students.repository.BulkImportJobRepository;
import com.edushift.modules.students.entity.BulkImportJob;
import com.edushift.modules.teachers.dto.CreateTeacherRequest;
import com.edushift.modules.teachers.dto.TeacherRowDraft;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.service.TeacherService;
import com.edushift.shared.exception.ApiException;
import com.edushift.shared.multitenancy.TenantContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Worker for teacher bulk-import jobs (Sprint cierre-B / F7).
 *
 * <p>Mirrors {@code StudentBulkImportRunner} 1:1. The runner is the
 * canonical place for the {@code @Async} dispatch; the service that
 * schedules jobs only does the synchronous bookkeeping.</p>
 *
 * <h3>Tenant context</h3>
 * The {@code bulkImportExecutor} carries the original tenant id into
 * the worker thread via
 * {@code com.edushift.infrastructure.async.ContextPropagatingTaskDecorator}.
 * As defence in depth the runner re-asserts it via {@link TenantContext#runAs}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeacherBulkImportRunner {

	private final TeacherBulkImportParser parser;
	private final TeacherService teacherService;
	private final BulkImportJobRepository jobRepository;
	private final Validator validator;

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
			log.warn("[bulk-import/teachers] job not found at runner start -- jobId={}", jobId);
			return;
		}
		job.markStarted();
		jobRepository.saveAndFlush(job);

		List<TeacherRowDraft> drafts;
		try {
			drafts = parser.parse(new ByteArrayInputStream(payload));
		}
		catch (BulkImportException e) {
			log.warn("[bulk-import/teachers] parse failed -- jobId={} code={} msg={}",
					jobId, e.getCode(), e.getMessage());
			job.markFailed(e.getMessage());
			jobRepository.saveAndFlush(job);
			return;
		}

		job.setTotalRows(drafts.size());
		jobRepository.saveAndFlush(job);

		Set<String> documentsInBatch = new HashSet<>();
		Set<String> emailsInBatch = new HashSet<>();
		for (TeacherRowDraft draft : drafts) {
			processOneRow(job, draft, documentsInBatch, emailsInBatch);
			job.incrementProcessed();
		}

		job.markCompleted();
		jobRepository.saveAndFlush(job);
		log.info("[bulk-import/teachers] completed -- jobId={} total={} errors={}",
				jobId, job.getTotalRows(), job.getErrorRows());
	}

	private void processOneRow(BulkImportJob job, TeacherRowDraft draft,
			Set<String> documentsInBatch, Set<String> emailsInBatch) {
		List<String> coercionErrors = collectCoercionErrors(draft);
		if (!coercionErrors.isEmpty()) {
			job.recordRowError(draft.rowNumber(), "ROW_INVALID", String.join("; ", coercionErrors));
			return;
		}

		String docKey = draft.documentType().name() + ":" + draft.documentNumber();
		if (!documentsInBatch.add(docKey)) {
			job.recordRowError(draft.rowNumber(), "ROW_DUPLICATE",
					"Duplicate document " + draft.documentType() + " " + draft.documentNumber());
			return;
		}
		String email = normaliseEmail(draft.email());
		if (email != null && !emailsInBatch.add(email)) {
			job.recordRowError(draft.rowNumber(), "ROW_DUPLICATE",
					"Duplicate email " + email);
			return;
		}

		CreateTeacherRequest request = toCreateRequest(draft);
		Set<ConstraintViolation<CreateTeacherRequest>> violations = validator.validate(request);
		if (!violations.isEmpty()) {
			String message = violations.stream()
					.map(v -> v.getPropertyPath() + ": " + v.getMessage())
					.collect(Collectors.joining("; "));
			job.recordRowError(draft.rowNumber(), "ROW_INVALID", message);
			return;
		}

		try {
			teacherService.createTeacher(request);
		}
		catch (ApiException e) {
			job.recordRowError(draft.rowNumber(), e.getCode(), e.getMessage());
		}
		catch (RuntimeException e) {
			job.recordRowError(draft.rowNumber(), "ROW_INVALID",
					e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
		}
	}

	private List<String> collectCoercionErrors(TeacherRowDraft draft) {
		List<String> errors = new ArrayList<>();
		if (draft.documentType() == null) {
			errors.add("documentType is required and must be one of: " + enumValues(DocumentType.class));
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
		return errors;
	}

	private CreateTeacherRequest toCreateRequest(TeacherRowDraft d) {
		List<String> specs = parseSpecializations(d.specializationsRaw());
		return new CreateTeacherRequest(
				d.documentType(),
				d.documentNumber(),
				d.firstName(),
				d.lastName(),
				d.secondLastName(),
				d.birthDate(),
				d.gender() == null ? Gender.NOT_SPECIFIED : d.gender(),
				d.email(),
				d.phone(),
				d.title(),
				specs,
				d.hireDate(),
				d.employmentStatus() == null ? EmploymentStatus.ACTIVE : d.employmentStatus(),
				null);
	}

	private static List<String> parseSpecializations(String raw) {
		if (raw == null || raw.isBlank()) return List.of();
		return Arrays.stream(raw.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
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