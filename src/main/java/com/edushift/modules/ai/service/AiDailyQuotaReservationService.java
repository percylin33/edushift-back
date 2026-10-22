package com.edushift.modules.ai.service;

import com.edushift.modules.ai.entity.TenantAiUsage;
import com.edushift.modules.ai.exception.AiQuotaExceededException;
import com.edushift.modules.ai.repository.TenantAiUsageRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserves one daily request in its own transaction before invoking a paid LLM.
 * PostgreSQL serializes callers for the same tenant, including across app instances.
 */
@Service
@RequiredArgsConstructor
public class AiDailyQuotaReservationService {

    private final TenantAiUsageRepository usageRepository;
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(UUID aiTenantId, int dailyLimit, long monthlyLimit) {
        jdbc.queryForObject(
                "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))) q",
                Integer.class, aiTenantId.toString());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        TenantAiUsage usage = TenantContext.runAs(aiTenantId,
                () -> usageRepository.findByUsageDay(today).orElse(null));
        int used = usage == null ? 0 : usage.getRequestCount();
        if (used >= dailyLimit) {
            throw new AiQuotaExceededException(
                    "Daily AI request quota exhausted for this tenant ("
                            + dailyLimit + " requests/day). The quota resets at 00:00 UTC.");
        }
        if (usageRepository.sumTokensThisMonth(aiTenantId) >= monthlyLimit) {
            throw new AiQuotaExceededException(
                    "Monthly AI token quota exhausted for this tenant ("
                            + monthlyLimit + " tokens/month). The quota resets next month UTC.");
        }
        usageRepository.incrementCounters(aiTenantId, today, 1, 0, 0, 0, 0);
    }
}
