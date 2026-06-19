package com.edushift.modules.ai.service;

import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.entity.TenantAiUsage;
import com.edushift.modules.ai.exception.AiDisabledException;
import com.edushift.modules.ai.exception.AiQuotaExceededException;
import com.edushift.modules.ai.repository.TenantAiSettingsRepository;
import com.edushift.modules.ai.repository.TenantAiUsageRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-level AI quota enforcement and counter maintenance
 * (BE-7c.1). This is the single point of contact for "may this
 * tenant make an AI call right now?" and "record that an AI
 * call happened". The {@code LmsAiService} calls into this on
 * every request.
 *
 * <h3>Quota semantics</h3>
 * <ul>
 *   <li><b>Master switch</b> ({@code ai_enabled}): if false → 403.</li>
 *   <li><b>Daily request quota</b> ({@code daily_request_quota}): if
 *       non-null and today's {@code request_count} would exceed it
 *       on increment → 429.</li>
 *   <li><b>Monthly token quota</b> ({@code monthly_token_quota}): if
 *       non-null and the sum of tokens this month would exceed it
 *       on increment → 429. (Checked optimistically: we don't know
 *       the token count yet; we pre-check the current sum and let
 *       the actual tokensIn/tokensOut settle the row.)</li>
 * </ul>
 *
 * <h3>Counters</h3>
 * The {@link #incrementCounters} method runs a single PostgreSQL
 * UPSERT (native query) so the counters are bumped atomically with
 * the rest of the call's transaction. A crash between LLM and DB
 * rolls back everything (LLM call, counters, audit row) consistently.
 */
@Service
public class AiQuotaService {

    private static final Logger log = LoggerFactory.getLogger(AiQuotaService.class);

    private final TenantAiSettingsRepository settingsRepo;
    private final TenantAiUsageRepository usageRepo;

    public AiQuotaService(TenantAiSettingsRepository settingsRepo,
                          TenantAiUsageRepository usageRepo) {
        this.settingsRepo = settingsRepo;
        this.usageRepo = usageRepo;
    }

    /**
     * Verifies the current tenant can make one more AI request right now.
     * Throws {@link AiDisabledException} or {@link AiQuotaExceededException}
     * if not. Otherwise returns the {@link TenantAiSettings} (for the
     * caller to read the {@code defaultModel}).
     */
    @Transactional(readOnly = true)
    public TenantAiSettings verifyCanCall() {
        TenantAiSettings settings = settingsRepo.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new AiDisabledException());
        if (!settings.isAiEnabled()) {
            throw new AiDisabledException();
        }
        LocalDate today = LocalDate.now();
        TenantAiUsage usage = usageRepo.findByUsageDay(today).orElse(null);
        int currentRequests = usage == null ? 0 : usage.getRequestCount();
        if (settings.getDailyRequestQuota() != null
                && currentRequests + 1 > settings.getDailyRequestQuota()) {
            throw new AiQuotaExceededException(
                    "Daily AI request quota exhausted for this tenant "
                            + "(" + settings.getDailyRequestQuota() + " requests/day). "
                            + "The quota resets at 00:00 UTC.");
        }
        if (settings.getMonthlyTokenQuota() != null) {
            UUID tenantId = TenantContext.currentRequired();
            long currentMonthTokens = usageRepo.sumTokensThisMonth(tenantId);
            if (currentMonthTokens >= settings.getMonthlyTokenQuota()) {
                throw new AiQuotaExceededException(
                        "Monthly AI token quota exhausted for this tenant "
                                + "(" + settings.getMonthlyTokenQuota() + " tokens/month). "
                                + "The quota resets on the 1st of next month UTC.");
            }
        }
        return settings;
    }

    /**
     * Atomically increments the daily counters for the current tenant.
     * Called by the {@code LmsAiService} after the LLM call resolves
     * (success or failure). Both success and failure are counted as a
     * request; only successful LLM responses add to {@code success_count}
     * and the token totals.
     */
    @Transactional
    public void incrementCounters(boolean success, long tokensIn, long tokensOut) {
        UUID tenantId = TenantContext.currentRequired();
        LocalDate today = LocalDate.now();
        int successDelta = success ? 1 : 0;
        int failedDelta  = success ? 0 : 1;
        long tokensInDelta  = success ? tokensIn  : 0L;
        long tokensOutDelta = success ? tokensOut : 0L;
        usageRepo.incrementCounters(tenantId, today, 1, successDelta, failedDelta,
                tokensInDelta, tokensOutDelta);
        log.debug("Incremented AI usage for tenant={} day={} success={} tokensIn={} tokensOut={}",
                tenantId, today, success, tokensIn, tokensOut);
    }
}
