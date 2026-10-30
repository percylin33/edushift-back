package com.edushift.modules.evaluations.competencyresult.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Complete replacement of the competency outcomes for an evaluation/student pair. */
public record UpsertStudentCompetencyResultsRequest(
		@NotNull @Size(max = 40) List<@Valid StudentCompetencyResultInput> results) {
}
