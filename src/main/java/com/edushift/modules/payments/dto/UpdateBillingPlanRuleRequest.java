package com.edushift.modules.payments.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateBillingPlanRuleRequest(@NotNull Boolean active) {}
