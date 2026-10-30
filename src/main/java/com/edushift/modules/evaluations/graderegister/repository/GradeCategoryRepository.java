package com.edushift.modules.evaluations.graderegister.repository;

import com.edushift.modules.evaluations.graderegister.entity.GradeCategory;
import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeCategoryRepository extends JpaRepository<GradeCategory, UUID> {

	Optional<GradeCategory> findByPublicUuid(UUID publicUuid);

	List<GradeCategory> findByTemplateOrderBySortOrderAscNameAsc(GradeRegisterTemplate template);

	boolean existsByTemplateAndNameIgnoreCase(GradeRegisterTemplate template, String name);
}
