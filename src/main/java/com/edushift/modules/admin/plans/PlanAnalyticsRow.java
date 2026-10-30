package com.edushift.modules.admin.plans;

import java.math.BigDecimal;
import java.util.UUID;

public record PlanAnalyticsRow(
        UUID planId,
        long activeInstitutions,
        long enrolledStudents,
        long projectedMrrCents,
        long aiTokensUsedThisMonth,
        long aiTokenQuotaThisMonth) {
}
