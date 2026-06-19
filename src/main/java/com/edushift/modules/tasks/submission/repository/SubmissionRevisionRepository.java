package com.edushift.modules.tasks.submission.repository;

import com.edushift.modules.tasks.submission.entity.Submission;
import com.edushift.modules.tasks.submission.entity.SubmissionRevision;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link SubmissionRevision}
 * (Sprint 7a / BE-7a.2, audit trail). Append-only.
 */
@Repository
public interface SubmissionRevisionRepository extends JpaRepository<SubmissionRevision, UUID> {

	/**
	 * Highest {@code revision_number} recorded for a submission.
	 * Returns {@code null} when the submission has no revisions
	 * yet (first-time submission path).
	 */
	@Query("select max(r.revisionNumber) from SubmissionRevision r "
			+ "where r.submission = :submission")
	Short findMaxRevisionNumber(@Param("submission") Submission submission);
}
