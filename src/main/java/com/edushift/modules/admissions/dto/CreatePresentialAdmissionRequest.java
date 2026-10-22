package com.edushift.modules.admissions.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePresentialAdmissionRequest(
        @NotNull UUID gradePublicUuid,
        @NotNull @Valid CreateAdmissionFromInquiryRequest applicant
) {}
