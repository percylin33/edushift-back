package com.edushift.modules.evaluations.competencyresult.repository;

import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.evaluations.competencyresult.entity.StudentCompetencyResult;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.students.entity.Student;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

@Repository
public interface StudentCompetencyResultRepository extends JpaRepository<StudentCompetencyResult, UUID> {

	@Query("select r from StudentCompetencyResult r where r.evaluation = :evaluation "
			+ "and r.student = :student and r.isActive = true "
			+ "order by r.competency.displayOrder asc")
	List<StudentCompetencyResult> findActive(@Param("evaluation") Evaluation evaluation,
			@Param("student") Student student);

	@Query("select r from StudentCompetencyResult r "
			+ "join fetch r.evaluation e "
			+ "join fetch e.teacherAssignment assignment "
			+ "join fetch assignment.course "
			+ "join fetch assignment.section "
			+ "join fetch r.competency competency "
			+ "where r.student = :student and r.isActive = true and e.status = :status "
			+ "order by e.scheduledDate desc, competency.displayOrder asc")
	List<StudentCompetencyResult> findFamilyVisibleResults(
			@Param("student") Student student,
			@Param("status") EvaluationStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from StudentCompetencyResult r where r.evaluation = :evaluation "
			+ "and r.student = :student and r.competency = :competency")
	Optional<StudentCompetencyResult> findForUpdate(@Param("evaluation") Evaluation evaluation,
			@Param("student") Student student, @Param("competency") Competency competency);

	@Query("select r from StudentCompetencyResult r where r.evaluation = :evaluation "
			+ "and r.student = :student and r.competency = :competency")
	Optional<StudentCompetencyResult> findOne(@Param("evaluation") Evaluation evaluation,
			@Param("student") Student student, @Param("competency") Competency competency);
}
