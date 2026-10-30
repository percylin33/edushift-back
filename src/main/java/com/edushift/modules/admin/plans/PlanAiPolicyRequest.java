package com.edushift.modules.admin.plans;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlanAiPolicyRequest(
        @NotNull @Min(0) Integer dailyRequestLimit,
        @NotNull @Min(0) Long monthlyTokenLimit,
        @NotNull @Min(0) Integer tutorMessagesPerStudentDay,
        @NotNull @Min(0) Integer institutionalDocumentLimit,
        @NotNull @Min(0) Long institutionalTextBytesLimit
) {}
