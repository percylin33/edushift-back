package com.edushift.modules.sessions.learning.repository;

import com.edushift.modules.sessions.learning.entity.ApplicationSheet;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationSheetRepository extends JpaRepository<ApplicationSheet, UUID> {

	@Query("""
			select s from ApplicationSheet s
			where s.learningSession.publicUuid = :sessionUuid
			order by s.kind asc, s.grade asc
			""")
	List<ApplicationSheet> findBySessionPublicUuid(@Param("sessionUuid") UUID sessionUuid);

	@Query("""
			select s from ApplicationSheet s
			where s.learningSession.publicUuid = :sessionUuid
			  and s.kind = :kind
			  and s.grade = :grade
			""")
	Optional<ApplicationSheet> findBySessionKindAndGrade(
			@Param("sessionUuid") UUID sessionUuid,
			@Param("kind") ApplicationSheetKind kind,
			@Param("grade") String grade);

	@Query("""
			select count(s) from ApplicationSheet s
			where s.learningSession.teacherAssignment.id = :assignmentId
			  and s.kind = :kind
			""")
	long countByAssignmentAndKind(
			@Param("assignmentId") UUID assignmentId,
			@Param("kind") ApplicationSheetKind kind);
}
