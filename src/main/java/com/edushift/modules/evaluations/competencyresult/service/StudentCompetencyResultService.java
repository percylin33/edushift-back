package com.edushift.modules.evaluations.competencyresult.service;

import com.edushift.modules.evaluations.competencyresult.dto.StudentCompetencyResultResponse;
import com.edushift.modules.evaluations.competencyresult.dto.StudentCompetencyResultRevisionResponse;
import com.edushift.modules.evaluations.competencyresult.dto.UpsertStudentCompetencyResultsRequest;
import java.util.List;
import java.util.UUID;

public interface StudentCompetencyResultService {
	List<StudentCompetencyResultResponse> list(UUID evaluationPublicUuid, UUID studentPublicUuid);

	List<StudentCompetencyResultRevisionResponse> history(UUID evaluationPublicUuid,
			UUID studentPublicUuid, UUID competencyPublicUuid);

	List<StudentCompetencyResultResponse> replace(UUID evaluationPublicUuid, UUID studentPublicUuid,
			UpsertStudentCompetencyResultsRequest request);
}
