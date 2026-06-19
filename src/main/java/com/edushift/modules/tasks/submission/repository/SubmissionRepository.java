package com.edushift.modules.tasks.submission.repository;

import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.submission.entity.Submission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Submission}
 * (Sprint 7a / BE-7a.2). Tenant-scoped automatically.
 */
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

	Optional<Submission> findByPublicUuid(UUID publicUuid);

	/**
	 * Find the current (non-deleted) submission for a
	 * {@code (task, student)} pair. The DB UNIQUE
	 * {@code uq_lms_submissions_task_student} guarantees at most
	 * one.
	 */
	Optional<Submission> findByTaskAndStudentUserId(Task task, UUID studentUserId);

	Page<Submission> findAllByTaskOrderByCreatedAtDesc(Task task, Pageable pageable);

	List<Submission> findAllByAttachmentPublicUuid(UUID attachmentPublicUuid);
}
