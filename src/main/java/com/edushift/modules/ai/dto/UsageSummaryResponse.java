package com.edushift.modules.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * AI usage summary response (Sprint 8 / BE-8.4, FE-8.4 dashboard).
 *
 * <p>Returned by {@code GET /v1/ai/usage/summary} to power the
 * TENANT_ADMIN dashboard: a quota meter, a pie chart by feature, and
 * a daily history table.
 *
 * @param periodStart          first day of the period (UTC, inclusive).
 * @param periodEnd            day after the last day of the period (UTC, exclusive).
 * @param dailyRequestQuota    configured cap for the period (or {@code null} = unlimited).
 * @param monthlyTokenQuota    configured token cap for the period (or {@code null}).
 * @param usedRequests         total LLM calls in the period.
 * @param usedTokens           total tokens in + out in the period.
 * @param successCount         calls that ended in {@code COMPLETED}.
 * @param failedCount          calls that ended in {@code FAILED} / {@code CANCELLED}.
 * @param byFeature            breakdown by {@code Feature} enum (only features with &gt;0 calls).
 * @param daily                daily breakdown (paginated subset; see {@code UsageController#daily}).
 * @param generatedAt          server timestamp for cache-busting on the FE.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsageSummaryResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer dailyRequestQuota,
        Long monthlyTokenQuota,
        long usedRequests,
        long usedTokens,
        long successCount,
        long failedCount,
        List<FeatureUsage> byFeature,
        List<DailyUsage> daily,
        Instant generatedAt
) {
    /**
     * One row in the per-feature breakdown.
     */
    public record FeatureUsage(
            String feature,
            long requestCount,
            long tokensIn,
            long tokensOut
    ) {}

    /**
     * One row in the daily history.
     */
    public record DailyUsage(
            LocalDate day,
            long requestCount,
            long successCount,
            long failedCount,
            long tokensIn,
            long tokensOut
    ) {}
}
