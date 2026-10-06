package com.edushift.modules.tasks.submission.repository;

import com.edushift.modules.tasks.entity.Task;
import com.edushift.modules.tasks.submission.entity.Submission;
import com.edushift.modules.tasks.submission.entity.SubmissionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

	@Query("""
			select s from Submission s
			join fetch s.task t
			left join fetch t.section
			left join fetch t.teacherAssignment a
			left join fetch a.course
			where s.studentUserId = :studentUserId and s.status = :status
			""")
	List<Submission> findAllByStudentUserIdAndStatus(
			@Param("studentUserId") UUID studentUserId,
			@Param("status") SubmissionStatus status);

	/** Active (non soft-deleted) submissions for one task — includes SUBMITTED and GRADED. */
	long countByTask(Task task);

	/** Active submissions for one task filtered by lifecycle status. */
	long countByTaskAndStatus(Task task, SubmissionStatus status);

	/**
	 * Batch count for list endpoints. Each row is {@code [taskId, count]}.
	 * Soft-deleted rows are excluded via {@code @SQLRestriction}.
	 */
	@Query("select s.task.id, count(s) from Submission s where s.task.id in :taskIds group by s.task.id")
	List<Object[]> countGroupedByTaskId(@Param("taskIds") Collection<UUID> taskIds);

	/**
	 * Batch count of graded submissions. Each row is {@code [taskId, count]}.
	 */
	@Query("""
			select s.task.id, count(s) from Submission s
			where s.task.id in :taskIds and s.status = com.edushift.modules.tasks.submission.entity.SubmissionStatus.GRADED
			group by s.task.id
			""")
	List<Object[]> countGradedGroupedByTaskId(@Param("taskIds") Collection<UUID> taskIds);
}
