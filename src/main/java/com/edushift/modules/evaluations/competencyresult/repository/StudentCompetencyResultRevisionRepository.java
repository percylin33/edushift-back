package com.edushift.modules.evaluations.competencyresult.repository;

import com.edushift.modules.evaluations.competencyresult.entity.StudentCompetencyResultRevision;
import com.edushift.modules.evaluations.competencyresult.entity.StudentCompetencyResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentCompetencyResultRevisionRepository
		extends JpaRepository<StudentCompetencyResultRevision, UUID> {
	List<StudentCompetencyResultRevision> findAllByResultOrderByRevisionDesc(StudentCompetencyResult result);
}
