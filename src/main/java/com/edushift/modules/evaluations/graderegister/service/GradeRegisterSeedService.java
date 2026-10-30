package com.edushift.modules.evaluations.graderegister.service;

import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateListItem;
import java.util.List;

public interface GradeRegisterSeedService {

	/** Idempotent per-tenant materialization of the system catalog. */
	List<GradeRegisterTemplateListItem> materializeCatalog();
}
