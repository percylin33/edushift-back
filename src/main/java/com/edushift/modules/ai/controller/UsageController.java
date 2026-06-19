package com.edushift.modules.ai.controller;

import com.edushift.modules.ai.dto.UsageSummaryResponse;
import com.edushift.modules.ai.dto.UsageSummaryResponse.DailyUsage;
import com.edushift.modules.ai.dto.UsageSummaryResponse.FeatureUsage;
import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.repository.TenantAiSettingsRepository;
import com.edushift.modules.ai.repository.TenantAiUsageRepository;
import com.edushift.modules.ai.repository.TenantAiUsageRepository.DailyUsageRow;
import com.edushift.modules.ai.repository.TenantAiUsageRepository.FeatureUsageRow;
import com.edushift.shared.multitenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI usage endpoints for the TENANT_ADMIN dashboard
 * (Sprint 8 / BE-8.4).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /v1/ai/usage/summary} — quota meter + per-feature
 *       breakdown + daily history (all in one shot, capped at 31 days
 *       for the month). Used by FE-8.4 on page load.</li>
 *   <li>{@code GET /v1/ai/usage/daily} — paginated daily history
 *       (used by FE-8.4 for "load more" and CSV export).</li>
 *   <li>{@code GET /v1/ai/usage/export.csv} — same data as
 *       {@code /daily} but as {@code text/csv} for the FE's
 *       "Export CSV" button.</li>
 * </ul>
 *
 * <h3>RBAC</h3>
 * All three endpoints require {@code LMS_AI_USAGE} (a new authority
 * added to TEACHER and TENANT_ADMIN in this sprint; see
 * {@code docs/product/roles-matrix.md}). STUDENT and PARENT do not
 * have access.
 *
 * <h3>Multi-tenant</h3>
 * The controller reads the tenant id from {@link TenantContext} (set
 * by the JWT filter). Every query is auto-filtered by Hibernate
 * {@code @TenantId} — no cross-tenant leak possible.
 */
@RestController
@RequestMapping("/v1/ai/usage")
@RequiredArgsConstructor
@Tag(name = "AI usage", description = "TENANT_ADMIN dashboard for AI quota + history (Sprint 8 / BE-8.4)")
public class UsageController {

    private final TenantAiUsageRepository usageRepo;
    private final TenantAiSettingsRepository settingsRepo;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('LMS_AI_USAGE')")
    @Operation(summary = "One-shot summary: quota meter + by-feature + daily history for the current UTC month")
    public ResponseEntity<UsageSummaryResponse> summary() {
        UUID tenantId = TenantContext.currentRequired();
        TenantAiSettings settings = settingsRepo.findFirstByOrderByIdAsc().orElse(null);

        // Period bounds: first day of the UTC month .. day after the last.
        LocalDate periodStart = LocalDate.now(java.time.ZoneOffset.UTC).withDayOfMonth(1);
        LocalDate periodEnd   = periodStart.plusMonths(1);

        // Daily history (cap 31 rows; for larger histories use /daily pagination).
        Page<DailyUsageRow> daily = usageRepo.findDailyUsageThisMonth(tenantId,
                PageRequest.of(0, 31));
        List<DailyUsage> dailyDtos = daily.stream()
                .map(r -> new DailyUsage(
                        r.getUsageDay(),
                        r.getRequestCount(),
                        r.getSuccessCount(),
                        r.getFailedCount(),
                        r.getTokensInTotal(),
                        r.getTokensOutTotal()))
                .toList();

        // Per-feature breakdown.
        List<FeatureUsage> byFeature = usageRepo.sumUsageThisMonthByFeature(tenantId).stream()
                .map(r -> new FeatureUsage(
                        r.getFeature(),
                        r.getRequestCount(),
                        r.getTokensInTotal(),
                        r.getTokensOutTotal()))
                .toList();

        // Aggregates.
        long usedRequests = dailyDtos.stream().mapToLong(DailyUsage::requestCount).sum();
        long usedTokens   = dailyDtos.stream().mapToLong(d -> d.tokensIn() + d.tokensOut()).sum();
        long successCount = dailyDtos.stream().mapToLong(DailyUsage::successCount).sum();
        long failedCount  = dailyDtos.stream().mapToLong(DailyUsage::failedCount).sum();

        UsageSummaryResponse body = new UsageSummaryResponse(
                periodStart, periodEnd,
                settings == null ? null : settings.getDailyRequestQuota(),
                settings == null ? null : settings.getMonthlyTokenQuota(),
                usedRequests, usedTokens, successCount, failedCount,
                byFeature, dailyDtos, Instant.now());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/daily")
    @PreAuthorize("hasAuthority('LMS_AI_USAGE')")
    @Operation(summary = "Paginated daily history (current UTC month). For 'load more' UX.")
    public ResponseEntity<Page<DailyUsage>> daily(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "31") int size) {
        UUID tenantId = TenantContext.currentRequired();
        int safeSize = Math.min(Math.max(size, 1), 90);
        Page<DailyUsageRow> raw = usageRepo.findDailyUsageThisMonth(tenantId,
                PageRequest.of(page, safeSize));
        Page<DailyUsage> mapped = raw.map(r -> new DailyUsage(
                r.getUsageDay(),
                r.getRequestCount(),
                r.getSuccessCount(),
                r.getFailedCount(),
                r.getTokensInTotal(),
                r.getTokensOutTotal()));
        return ResponseEntity.ok(mapped);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('LMS_AI_USAGE')")
    @Operation(summary = "CSV export of the daily history (for spreadsheets / reports)")
    public ResponseEntity<String> exportCsv(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "90") int size) {
        UUID tenantId = TenantContext.currentRequired();
        int safeSize = Math.min(Math.max(size, 1), 365);
        Page<DailyUsageRow> raw = usageRepo.findDailyUsageThisMonth(tenantId,
                PageRequest.of(page, safeSize));
        StringBuilder sb = new StringBuilder(64 + raw.getNumberOfElements() * 80);
        sb.append("day,request_count,success_count,failed_count,tokens_in,tokens_out\n");
        for (DailyUsageRow r : raw) {
            sb.append(r.getUsageDay()).append(',')
              .append(r.getRequestCount()).append(',')
              .append(r.getSuccessCount()).append(',')
              .append(r.getFailedCount()).append(',')
              .append(r.getTokensInTotal()).append(',')
              .append(r.getTokensOutTotal()).append('\n');
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header("Content-Disposition", "attachment; filename=\"edushift-ai-usage.csv\"")
                .body(sb.toString());
    }
}
