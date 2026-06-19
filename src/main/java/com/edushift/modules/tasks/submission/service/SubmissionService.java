package com.edushift.modules.tasks.submission.service;

import com.edushift.modules.tasks.submission.dto.CreateSubmissionRequest;
import com.edushift.modules.tasks.submission.dto.GradeSubmissionRequest;
import com.edushift.modules.tasks.submission.dto.SubmissionResponse;
import com.edushift.modules.tasks.submission.dto.SubmissionSummary;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public contract for the LMS submissions sub-module
 * (Sprint 7a / BE-7a.2).
 */
public interface SubmissionService {

	/**
	 * Submit (or re-submit) on behalf of a student. The bearer is
	 * either the student themselves or a parent. The
	 * {@code studentPublicUuid} in the body identifies the target.
	 *
	 * @return the (possibly updated) {@link SubmissionResponse}; the
	 *         {@code wasIdempotent} flag of the response is
	 *         {@code true} when a re-submit was accepted.
	 */
	SubmissionResponse submit(UUID taskPublicUuid, CreateSubmissionRequest request,
			UUID submitterUserId);

	Page<SubmissionSummary> listByTask(UUID taskPublicUuid, Pageable pageable);

	/**
	 * Get the current submission for a (task, student) pair. The
	 * student is either the bearer (self) or the parent's child
	 * (queried via {@code studentPublicUuid} parameter).
	 *
	 * @return the {@link SubmissionResponse} or {@code null} when no
	 *         submission exists yet.
	 */
	SubmissionResponse getMine(UUID taskPublicUuid, UUID studentPublicUuid);

	/**
	 * Grade a submission. Sets {@code status=GRADED}, {@code grade},
	 * {@code feedback}, {@code gradedByUserId}, {@code gradedAt}.
	 */
	SubmissionResponse grade(UUID submissionPublicUuid, GradeSubmissionRequest request,
			UUID graderUserId);
}
