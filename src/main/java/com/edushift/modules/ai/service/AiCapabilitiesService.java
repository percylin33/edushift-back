package com.edushift.modules.ai.service;

import com.edushift.modules.ai.dto.AiCapabilitiesResponse;
import com.edushift.modules.ai.exception.AiPlanUnavailableException;
import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.entity.TenantAiUsage;
import com.edushift.modules.ai.repository.TenantAiSettingsRepository;
import com.edushift.modules.ai.repository.TenantAiUsageRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.LmsAuthorities;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiCapabilitiesService {

    private final AiEntitlementService entitlementService;
    private final AiTenantKeyResolver tenantKeyResolver;
    private final TenantAiSettingsRepository settingsRepository;
    private final TenantAiUsageRepository usageRepository;
    private final AiMonthlyTokenReservationService monthlyReservations;

    @Transactional(readOnly = true)
    public AiCapabilitiesResponse current(Authentication authentication) {
        AiEntitlementService.EffectiveAiPlan plan;
        try {
            plan = entitlementService.current();
        } catch (AiPlanUnavailableException ex) {
            return new AiCapabilitiesResponse(null, false,
                    Map.of("TUTOR", false, "TEACHER", false, "FAMILY", false),
                    0, 0, LocalDate.now(ZoneOffset.UTC).plusDays(1)
                    .atStartOfDay(ZoneOffset.UTC).toInstant(), "AI_PLAN_UNAVAILABLE");
        }
        UUID aiTenantId = tenantKeyResolver.resolve(TenantContext.currentRequired());
        TenantAiSettings settings = settingsRepository.findActiveByTenantId(aiTenantId)
                .orElse(null);
        boolean enabled = settings != null && settings.isAiEnabled();

        Map<String, Boolean> assistants = new LinkedHashMap<>();
        assistants.put("TUTOR", enabled && allowed(authentication,
                LmsAuthorities.AI_TUTOR_USE)
                && plan.features().contains(AiEntitlementService.TUTOR));
        assistants.put("TEACHER", enabled && allowed(authentication,
                LmsAuthorities.AI_TEACHER_USE)
                && plan.features().contains(AiEntitlementService.TEACHER));
        assistants.put("FAMILY", enabled && allowed(authentication,
                LmsAuthorities.AI_FAMILY_USE)
                && plan.features().contains(AiEntitlementService.FAMILY));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        TenantAiUsage usage = TenantContext.runAs(aiTenantId,
                () -> usageRepository.findByUsageDay(today).orElse(null));
        int effectiveDaily = settings == null || settings.getDailyRequestQuota() == null
                ? plan.dailyRequestLimit()
                : Math.min(plan.dailyRequestLimit(), settings.getDailyRequestQuota());
        long effectiveMonthly = settings == null || settings.getMonthlyTokenQuota() == null
                ? plan.monthlyTokenLimit()
                : Math.min(plan.monthlyTokenLimit(), settings.getMonthlyTokenQuota());
        int usedToday = usage == null ? 0 : usage.getRequestCount();
        long usedMonth = usageRepository.sumTokensThisMonth(aiTenantId)
                + monthlyReservations.heldThisMonth(aiTenantId);
        int remainingDaily = Math.max(0, effectiveDaily - usedToday);
        long remainingMonthly = Math.max(0L, effectiveMonthly - usedMonth);
        if (remainingDaily == 0 || remainingMonthly == 0) {
            assistants.replaceAll((key, value) -> false);
        }
        String reason = !enabled ? "AI_DISABLED"
                : remainingDaily == 0 || remainingMonthly == 0 ? "AI_QUOTA_EXCEEDED"
                : assistants.values().stream().noneMatch(Boolean::booleanValue)
                ? "AI_FEATURE_OR_ROLE_UNAVAILABLE" : null;
        return new AiCapabilitiesResponse(plan.planCode(), enabled, Map.copyOf(assistants),
                remainingDaily, remainingMonthly,
                today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(), reason);
    }

    private static boolean allowed(Authentication authentication, String authority) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
