package com.edushift.modules.students.service.bulk;

import com.edushift.modules.students.dto.AddGuardianRequest;
import com.edushift.modules.students.dto.CreateStudentRequest;
import com.edushift.modules.students.dto.StudentResponse;
import com.edushift.modules.students.enrollments.dto.CreateEnrollmentRequest;
import com.edushift.modules.students.enrollments.service.StudentEnrollmentService;
import com.edushift.modules.students.entity.BulkImportJob;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.RelationshipType;
import com.edushift.modules.students.repository.BulkImportJobRepository;
import com.edushift.modules.students.service.StudentGuardianService;
import com.edushift.modules.students.service.StudentService;
import com.edushift.shared.exception.ApiException;
import com.edushift.shared.multitenancy.TenantContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Async worker for student bulk-import jobs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentBulkImportRunner {

	private final StudentBulkImportParser parser;
	private final StudentService studentService;
	private final StudentGuardianService guardianService;
	private final StudentEnrollmentService enrollmentService;
	private final BulkImportJobRepository jobRepository;
	private final Validator validator;

	@Async("bulkImportExecutor")
	public void run(UUID jobId, UUID tenantId, byte[] payload) {
		run(jobId, tenantId, payload, StudentBulkImportOptions.none());
	}

	@Async("bulkImportExecutor")
	public void run(UUID jobId, UUID tenantId, byte[] payload,
			StudentBulkImportOptions options) {
		TenantContext.runAs(tenantId, () -> {
			processJob(jobId, payload, options == null
					? StudentBulkImportOptions.none() : options);
			return null;
		});
	}

	@Transactional
	void processJob(UUID jobId, byte[] payload) {
		processJob(jobId, payload, StudentBulkImportOptions.none());
	}

	@Transactional
	void processJob(UUID jobId, byte[] payload, StudentBulkImportOptions options) {
		BulkImportJob job = jobRepository.findById(jobId).orElse(null);
		if (job == null) {
			log.warn("[bulk-import] job not found at runner start -- jobId={}", jobId);
			return;
		}
		job.markStarted();
		jobRepository.saveAndFlush(job);

		List<StudentRowDraft> drafts;
		try {
			drafts = parser.parse(new ByteArrayInputStream(payload),
					options.columnMapping());
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
			processOneRow(job, draft, documentsInBatch, emailsInBatch, options);
			job.incrementProcessed();
		}

		job.markCompleted();
		jobRepository.saveAndFlush(job);
		log.info("[bulk-import] completed -- jobId={} total={} errors={}",
				jobId, job.getTotalRows(), job.getErrorRows());
	}

	private void processOneRow(BulkImportJob job, StudentRowDraft draft,
			Set<String> documentsInBatch, Set<String> emailsInBatch,
			StudentBulkImportOptions options) {
		List<String> coercionErrors = collectCoercionErrors(draft);
		if (!coercionErrors.isEmpty()) {
			failRow(job, draft, "ROW_INVALID", String.join("; ", coercionErrors));
			return;
		}

		String docKey = draft.documentType().name() + ":" + draft.documentNumber();
		if (!documentsInBatch.add(docKey)) {
			failRow(job, draft, "ROW_DUPLICATE",
					"Duplicate document " + draft.documentType()
							+ " " + draft.documentNumber() + " inside the same upload");
			return;
		}
		String email = normaliseEmail(draft.email());
		if (email != null && !emailsInBatch.add(email)) {
			failRow(job, draft, "ROW_DUPLICATE",
					"Duplicate email " + email + " inside the same upload");
			return;
		}

		CreateStudentRequest request = toCreateRequest(draft, options);
		Set<ConstraintViolation<CreateStudentRequest>> violations = validator.validate(request);
		if (!violations.isEmpty()) {
			String message = violations.stream()
					.map(v -> v.getPropertyPath() + ": " + v.getMessage())
					.collect(Collectors.joining("; "));
			failRow(job, draft, "ROW_INVALID", message);
			return;
		}

		if (draft.hasAnyGuardianField()) {
			AddGuardianRequest guardianRequest = toAddGuardianRequest(draft);
			Set<ConstraintViolation<AddGuardianRequest>> gViolations =
					validator.validate(guardianRequest);
			if (!gViolations.isEmpty()) {
				String message = gViolations.stream()
						.map(v -> v.getPropertyPath() + ": " + v.getMessage())
						.collect(Collectors.joining("; "));
				failRow(job, draft, "ROW_INVALID", "Apoderado: " + message);
				return;
			}
		}

		StudentResponse created;
		try {
			created = studentService.createStudent(request);
		}
		catch (ApiException e) {
			failRow(job, draft, e.getCode(), e.getMessage());
			return;
		}
		catch (RuntimeException e) {
			failRow(job, draft, "ROW_INVALID",
					e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
			return;
		}

		if (options.hasSection()) {
			try {
				LocalDate enrolledAt = options.enrolledAt() != null
						? options.enrolledAt() : LocalDate.now();
				enrollmentService.createEnrollment(created.publicUuid(),
						new CreateEnrollmentRequest(
								options.sectionPublicUuid(),
								options.academicYearPublicUuid(),
								enrolledAt,
								"bulk-import"));
			}
			catch (ApiException e) {
				failRow(job, draft, "ENROLLMENT_FAILED",
						"Student " + created.publicUuid() + " created but enrollment failed: "
								+ e.getMessage(),
						created.publicUuid().toString());
				return;
			}
			catch (RuntimeException e) {
				failRow(job, draft, "ENROLLMENT_FAILED",
						"Student " + created.publicUuid() + " created but enrollment failed: "
								+ (e.getMessage() == null ? e.getClass().getSimpleName()
								: e.getMessage()),
						created.publicUuid().toString());
				return;
			}
		}

		if (draft.hasAnyGuardianField()) {
			try {
				guardianService.addGuardian(created.publicUuid(), toAddGuardianRequest(draft));
			}
			catch (ApiException e) {
				failRow(job, draft, "GUARDIAN_LINK_FAILED",
						"Student " + created.publicUuid() + " created but guardian failed: "
								+ e.getMessage(),
						created.publicUuid().toString());
			}
			catch (RuntimeException e) {
				failRow(job, draft, "GUARDIAN_LINK_FAILED",
						"Student " + created.publicUuid() + " created but guardian failed: "
								+ (e.getMessage() == null ? e.getClass().getSimpleName()
								: e.getMessage()),
						created.publicUuid().toString());
			}
		}
	}

	private void failRow(BulkImportJob job, StudentRowDraft draft, String code, String message) {
		failRow(job, draft, code, message, null);
	}

	private void failRow(BulkImportJob job, StudentRowDraft draft, String code, String message,
			String studentPublicUuid) {
		Map<String, String> fields = snapshot(draft);
		if (studentPublicUuid != null) {
			fields.put("studentPublicUuid", studentPublicUuid);
		}
		job.recordRowError(draft.rowNumber(), code, message, studentLabel(draft), fields);
	}

	private static String studentLabel(StudentRowDraft draft) {
		String name = joinName(draft.firstName(), draft.lastName());
		String document = draft.documentType() == null
				? blankToNull(draft.documentNumber())
				: (draft.documentNumber() == null || draft.documentNumber().isBlank()
						? draft.documentType().name()
						: draft.documentType().name() + " " + draft.documentNumber().trim());
		if (name != null && document != null) {
			return name + " · " + document;
		}
		return name != null ? name : document;
	}

	private static Map<String, String> snapshot(StudentRowDraft draft) {
		Map<String, String> fields = new LinkedHashMap<>();
		put(fields, "documentType", draft.documentType() == null ? null : draft.documentType().name());
		put(fields, "documentNumber", draft.documentNumber());
		put(fields, "firstName", draft.firstName());
		put(fields, "lastName", draft.lastName());
		put(fields, "email", draft.email());
		return fields;
	}

	private static void put(Map<String, String> fields, String key, String value) {
		String trimmed = blankToNull(value);
		if (trimmed != null) {
			fields.put(key, trimmed);
		}
	}

	private static String joinName(String first, String last) {
		String left = blankToNull(first);
		String right = blankToNull(last);
		if (left == null) {
			return right;
		}
		if (right == null) {
			return left;
		}
		return left + " " + right;
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private List<String> collectCoercionErrors(StudentRowDraft draft) {
		List<String> errors = new ArrayList<>();
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
		if (draft.hasAnyGuardianField() && !draft.hasCompleteGuardian()) {
			errors.add("guardian requires documentType, documentNumber, firstName and lastName");
		}
		return errors;
	}

	private CreateStudentRequest toCreateRequest(StudentRowDraft d,
			StudentBulkImportOptions options) {
		EnrollmentStatus status = d.enrollmentStatus();
		LocalDate enrollmentDate = d.enrollmentDate();
		if (options.hasSection()) {
			status = EnrollmentStatus.ENROLLED;
			if (enrollmentDate == null) {
				enrollmentDate = options.enrolledAt() != null
						? options.enrolledAt() : LocalDate.now();
			}
		}
		else if (status == null) {
			status = EnrollmentStatus.PENDING;
		}
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
				status,
				enrollmentDate,
				null
		);
	}

	private AddGuardianRequest toAddGuardianRequest(StudentRowDraft d) {
		RelationshipType relationship = d.guardianRelationship() == null
				? RelationshipType.GUARDIAN : d.guardianRelationship();
		return new AddGuardianRequest(
				d.guardianDocumentType(),
				d.guardianDocumentNumber(),
				d.guardianFirstName(),
				d.guardianLastName(),
				d.guardianEmail(),
				d.guardianPhone(),
				null,
				relationship,
				true,
				true
		);
	}

	private static String normaliseEmail(String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.trim();
		return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
	}

	private static <E extends Enum<E>> String enumValues(Class<E> type) {
		StringBuilder sb = new StringBuilder();
		for (E v : type.getEnumConstants()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(v.name());
		}
		return sb.toString();
	}
}
