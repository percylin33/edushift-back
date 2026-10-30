package com.edushift.modules.evaluations.graderegister.service;

import com.edushift.modules.evaluations.graderegister.dto.AdoptGradeRegisterTemplateRequest;
import com.edushift.modules.evaluations.graderegister.dto.CreateGradeCategoryRequest;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateListItem;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateResponse;
import com.edushift.modules.evaluations.graderegister.dto.UpdateGradeCategoryRequest;
import com.edushift.modules.evaluations.graderegister.dto.UpdateGradeRegisterTemplateRequest;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategory;
import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplate;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradeRegisterTemplateService {

	List<GradeRegisterTemplateListItem> listCatalog();

	List<GradeRegisterTemplateListItem> listMine(UUID academicYearPublicUuid);

	GradeRegisterTemplateResponse get(UUID publicUuid);

	GradeRegisterTemplateResponse adopt(UUID catalogPublicUuid, AdoptGradeRegisterTemplateRequest request);

	GradeRegisterTemplateResponse update(UUID publicUuid, UpdateGradeRegisterTemplateRequest request);

	void delete(UUID publicUuid);

	GradeRegisterTemplateResponse addCategory(UUID templateUuid, CreateGradeCategoryRequest request);

	GradeRegisterTemplateResponse updateCategory(UUID categoryUuid, UpdateGradeCategoryRequest request);

	void deleteCategory(UUID categoryUuid);

	/**
	 * Working template entity for a teacher assignment.
	 * Order: COURSE, then LEVEL of the section, then TENANT default.
	 */
	Optional<GradeRegisterTemplate> resolveTemplate(TeacherAssignment assignment);

	/** DTO view of {@link #resolveTemplate(TeacherAssignment)}. */
	Optional<GradeRegisterTemplateResponse> resolveForAssignment(TeacherAssignment assignment);

	Optional<GradeCategory> findCategoryByPublicUuid(UUID categoryPublicUuid);
}
