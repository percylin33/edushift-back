package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateAdmissionApplicationRequest(
		@NotNull UUID studentPublicUuid,
		@NotNull UUID gradePublicUuid,
		@NotNull AdmissionSource source,
		@Size(max = 2000) String note
) {}
