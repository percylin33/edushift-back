package com.edushift.modules.tasks.submission.service.impl;

import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.exception.TaskNotFoundException;
import com.edushift.modules.tasks.repository.TaskRepository;
import com.edushift.modules.tasks.submission.dto.CreateSubmissionRequest;
import com.edushift.modules.tasks.submission.dto.GradeSubmissionRequest;
import com.edushift.modules.tasks.submission.dto.SubmissionResponse;
import com.edushift.modules.tasks.submission.dto.SubmissionSummary;
import com.edushift.modules.tasks.submission.entity.Submission;
import com.edushift.modules.tasks.submission.entity.SubmissionRevision;
import com.edushift.modules.tasks.submission.entity.SubmissionStatus;
import com.edushift.modules.tasks.submission.exception.AssignmentPastDueException;
import com.edushift.modules.tasks.submission.exception.GradeOutOfRangeException;
import com.edushift.modules.tasks.submission.exception.ResubmissionNotAllowedException;
import com.edushift.modules.tasks.submission.exception.SubmissionNotFoundException;
import com.edushift.modules.tasks.submission.mapper.SubmissionMapper;
import com.edushift.modules.tasks.submission.repository.SubmissionRepository;
import com.edushift.modules.tasks.submission.repository.SubmissionRevisionRepository;
import com.edushift.modules.tasks.submission.service.SubmissionService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link SubmissionService} implementation
 * (Sprint 7a / BE-7a.2).
 *
 * <h3>Authorisation (D-TSK-07, REQ-TSK-06, REQ-TSK-09)</h3>
 * Coarse {@code @PreAuthorize} at the controller; the service
 * performs:
 * <ul>
 *   <li>parent-link check: a parent submitting on behalf must be
 *       linked to the target student. The actual relationship
 *       lookup is a future work (DEBT-7A-23): the v1 implementation
 *       only validates that the bearer is the target student
 *       (self-submit). Cross-bearer parent submissions are accepted
 *       with a {@code submitterUserId} ≠ {@code studentUserId} but
 *       without a DB link check — flagged for follow-up.</li>
 *   <li>enrollment check: a student must be enrolled in the task's
 *       section. v1 stub: a {@code SectionEnrollmentService} does
 *       not exist yet (DEBT-7A-24), so this check is
 *       documentation-only. Cross-tenant isolation is still
 *       guaranteed by the {@code @TenantId} filter.</li>
 *   <li>owner / section check: a teacher grading a submission
 *       must own the task's section. v1 stub: any teacher with
 *       {@code LMS_TASK_GRADE} can grade (DEBT-7A-25 follow-up:
 *       enforce section ownership).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

	private final TaskRepository taskRepository;
	private final SubmissionRepository submissionRepository;
	private final SubmissionRevisionRepository revisionRepository;
	private final FileObjectService fileObjectService;
	private final SubmissionMapper submissionMapper;

	@Override
	@Transactional
	public SubmissionResponse submit(UUID taskPublicUuid, CreateSubmissionRequest request,
			UUID submitterUserId) {
		Task task = requireTask(taskPublicUuid);
		validatePayloadNotEmpty(request);
		validateNotPastDue(task);

		Optional<Submission> existing = submissionRepository.findByTaskAndStudentUserId(
				task, request.studentPublicUuid());
		boolean isReSubmit = existing.isPresent();

		if (isReSubmit && !task.isAllowResubmission()) {
			throw new ResubmissionNotAllowedException();
		}

		Submission entity = existing.orElseGet(Submission::new);
		// Re-submit path: snapshot the previous payload into a
		// revision BEFORE mutating the current row.
		if (isReSubmit) {
			snapshotRevision(entity, submitterUserId);
			// Release the previous attachment (if any) so that the
			// new (or null) attachment can be acquired cleanly.
			UUID oldAttachment = entity.getAttachmentPublicUuid();
			if (oldAttachment != null) {
				fileObjectService.releaseReference(oldAttachment);
			}
		}

		entity.setTask(task);
		entity.setStudentUserId(request.studentPublicUuid());
		entity.setSubmitterUserId(submitterUserId);
		entity.setTextBody(request.textBody());
		entity.setAttachmentPublicUuid(request.attachmentPublicUuid());
		entity.setStatus(SubmissionStatus.SUBMITTED);
		// A re-submission clears the previous grade (teacher must
		// re-grade). DB CHECK allows grade=NULL for status=SUBMITTED.
		entity.setGrade(null);
		entity.setFeedback(null);
		entity.setGradedByUserId(null);
		entity.setGradedAt(null);

		Submission saved = submissionRepository.save(entity);

		// Acquire reference for the new attachment (if any).
		if (saved.getAttachmentPublicUuid() != null) {
			validateFileInTenant(saved.getAttachmentPublicUuid());
			fileObjectService.acquireReference(saved.getAttachmentPublicUuid());
		}

		// (D-TSK-07) section-enrollment check is a stub in v1
		// (DEBT-7A-24 follow-up).

		return submissionMapper.toResponse(saved, isReSubmit);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<SubmissionSummary> listByTask(UUID taskPublicUuid, Pageable pageable) {
		Task task = requireTask(taskPublicUuid);
		return submissionRepository
				.findAllByTaskOrderByCreatedAtDesc(task, pageable)
				.map(submissionMapper::toSummary);
	}

	@Override
	@Transactional(readOnly = true)
	public SubmissionResponse getMine(UUID taskPublicUuid, UUID studentPublicUuid) {
		Task task = requireTask(taskPublicUuid);
		Optional<Submission> entity = submissionRepository
				.findByTaskAndStudentUserId(task, studentPublicUuid);
		// Spec: 200 with data=null when no submission exists.
		return entity.map(submissionMapper::toResponse).orElse(null);
	}

	@Override
	@Transactional
	public SubmissionResponse grade(UUID submissionPublicUuid, GradeSubmissionRequest request,
			UUID graderUserId) {
		// Range re-checked in the service because Bean Validation
		// does not guard against bad controller input.
		if (request.grade() < 0 || request.grade() > 100) {
			throw new GradeOutOfRangeException(request.grade());
		}
		Submission entity = submissionRepository.findByPublicUuid(submissionPublicUuid)
				.orElseThrow(() -> new SubmissionNotFoundException(submissionPublicUuid.toString()));

		entity.setGrade((short) request.grade().intValue());
		entity.setFeedback(request.feedback());
		entity.setGradedByUserId(graderUserId);
		entity.setGradedAt(Instant.now());
		entity.setStatus(SubmissionStatus.GRADED);

		Submission saved = submissionRepository.save(entity);
		// (D-TSK-07) teacher-must-own-section check is a stub in v1
		// (DEBT-7A-25 follow-up).
		return submissionMapper.toResponse(saved);
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private Task requireTask(UUID publicUuid) {
		return taskRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new TaskNotFoundException(publicUuid.toString()));
	}

	private static void validateNotPastDue(Task task) {
		if (task.getDueAt() != null && Instant.now().isAfter(task.getDueAt())) {
			throw new AssignmentPastDueException();
		}
	}

	private static void validatePayloadNotEmpty(CreateSubmissionRequest request) {
		if (request.textBody() == null && request.attachmentPublicUuid() == null) {
			// Mirrors the DB CHECK chk_lms_submissions_payload_not_empty
			// with a friendlier error.
			throw new com.edushift.shared.exception.BadRequestException(
					"INCONSISTENT_PAYLOAD",
					"Submission must have at least one of textBody or attachmentPublicUuid.");
		}
	}

	private void validateFileInTenant(UUID filePublicUuid) {
		Optional<FileObject> file = fileObjectService.findByPublicUuid(filePublicUuid);
		if (file.isEmpty()) {
			throw new com.edushift.shared.exception.BadRequestException(
					"FILE_NOT_FOUND",
					"attachmentPublicUuid not found in tenant: " + filePublicUuid);
		}
	}

	private void snapshotRevision(Submission previous, UUID createdByUserId) {
		SubmissionRevision rev = new SubmissionRevision();
		rev.setSubmission(previous);
		rev.setRevisionNumber(nextRevisionNumber(previous));
		rev.setTextBody(previous.getTextBody());
		rev.setAttachmentPublicUuid(previous.getAttachmentPublicUuid());
		rev.setCreatedByUserId(createdByUserId);
		revisionRepository.save(rev);
	}

	private short nextRevisionNumber(Submission submission) {
		Short current = revisionRepository.findMaxRevisionNumber(submission);
		return (short) (current == null ? 1 : current + 1);
	}
}
