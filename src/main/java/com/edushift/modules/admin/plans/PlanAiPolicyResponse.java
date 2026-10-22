package com.edushift.modules.admin.plans;

import java.util.UUID;

public record PlanAiPolicyResponse(
        UUID planId,
        int dailyRequestLimit,
        long monthlyTokenLimit,
        int tutorMessagesPerStudentDay,
        int institutionalDocumentLimit,
        long institutionalTextBytesLimit
) {
    static PlanAiPolicyResponse from(PlanAiPolicy policy) {
        return new PlanAiPolicyResponse(policy.getPlanId(), policy.getDailyRequestLimit(),
                policy.getMonthlyTokenLimit(), policy.getTutorMessagesPerStudentDay(),
                policy.getInstitutionalDocumentLimit(),
                policy.getInstitutionalTextBytesLimit());
    }
}
