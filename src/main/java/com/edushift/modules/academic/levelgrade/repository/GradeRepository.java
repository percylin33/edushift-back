package com.edushift.modules.academic.levelgrade.repository;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Grade}.
 */
@Repository
public interface GradeRepository extends JpaRepository<Grade, UUID> {

	Optional<Grade> findByPublicUuid(UUID publicUuid);

	List<Grade> findAllByLevelOrderByOrdinalAsc(AcademicLevel level);

	long countByLevel(AcademicLevel level);

	Optional<Grade> findByLevelAndOrdinal(AcademicLevel level, Integer ordinal);
}
