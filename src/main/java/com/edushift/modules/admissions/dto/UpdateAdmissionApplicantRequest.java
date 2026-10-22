package com.edushift.modules.admissions.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAdmissionApplicantRequest(
        @NotNull @Valid CreateAdmissionFromInquiryRequest applicant,
        @NotBlank @Size(max = 500) String reason) {}
