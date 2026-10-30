package com.edushift.modules.admin.plans;

import com.edushift.shared.exception.NotFoundException;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanAiPolicyService {

    private final PlatformPlanRepository planRepository;
    private final PlanAiPolicyRepository policyRepository;
    private final AuditLogger auditLogger;

    @Transactional(readOnly = true)
    public List<PlanAiPolicyResponse> list() {
        return policyRepository.findAll().stream().map(PlanAiPolicyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PlanAiPolicyResponse get(UUID planId) {
        return policyRepository.findByPlanId(planId)
                .map(PlanAiPolicyResponse::from)
                .orElseThrow(() -> new NotFoundException(
                        "PLAN_AI_POLICY_NOT_FOUND", "AI policy not found for this plan"));
    }

    @Transactional
    public PlanAiPolicyResponse save(UUID planId, PlanAiPolicyRequest request) {
        planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("PLAN_NOT_FOUND", "Plan not found"));
        PlanAiPolicy policy = policyRepository.findByPlanId(planId)
                .orElseGet(() -> {
                    PlanAiPolicy created = new PlanAiPolicy();
                    created.setPlanId(planId);
                    return created;
                });
        policy.setDailyRequestLimit(request.dailyRequestLimit());
        policy.setMonthlyTokenLimit(request.monthlyTokenLimit());
        policy.setTutorMessagesPerStudentDay(request.tutorMessagesPerStudentDay());
        policy.setInstitutionalDocumentLimit(request.institutionalDocumentLimit());
        policy.setInstitutionalTextBytesLimit(request.institutionalTextBytesLimit());
        PlanAiPolicy saved = policyRepository.save(policy);
        auditLogger.log(AuditAction.CONFIG_CHANGED, "plan_ai_policy", saved.getId(),
                "Updated AI quotas for platform plan", java.util.Map.of("planId", planId.toString()));
        return PlanAiPolicyResponse.from(saved);
    }
}
