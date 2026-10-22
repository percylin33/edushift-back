package com.edushift.modules.ai.service;

import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.exception.AiDisabledException;
import com.edushift.modules.ai.exception.AiQuotaExceededException;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.ai.repository.TenantAiSettingsRepository;
import com.edushift.modules.ai.repository.TenantAiUsageRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
 *   <li><b>Daily request quota</b>: reserved atomically before the LLM call.</li>
 *   <li><b>Monthly token quota</b>: a conservative per-request token
 *       bound is reserved before the provider call. Actual usage settles
 *       the reservation once and releases the unused hold.</li>
 * </ul>
 *
 * <h3>Counters</h3>
 * Request reservations and outcome counters commit independently of the
 * generation transaction so a rollback cannot erase a paid call.
 */
@Service
public class AiQuotaService {

    private static final Logger log = LoggerFactory.getLogger(AiQuotaService.class);

    private final TenantAiSettingsRepository settingsRepo;
    private final TenantAiUsageRepository usageRepo;
    private final AiTenantKeyResolver aiTenantKeyResolver;
    private final AiEntitlementService entitlementService;
    private final AiDailyQuotaReservationService reservationService;
    private final AiMonthlyTokenReservationService monthlyReservations;
    private final ThreadLocal<Pending> pending = new ThreadLocal<>();

    public AiQuotaService(TenantAiSettingsRepository settingsRepo,
                          TenantAiUsageRepository usageRepo,
                          AiTenantKeyResolver aiTenantKeyResolver,
                          AiEntitlementService entitlementService,
                          AiDailyQuotaReservationService reservationService,
                          AiMonthlyTokenReservationService monthlyReservations) {
        this.settingsRepo = settingsRepo;
        this.usageRepo = usageRepo;
        this.aiTenantKeyResolver = aiTenantKeyResolver;
        this.entitlementService = entitlementService;
        this.reservationService = reservationService;
        this.monthlyReservations = monthlyReservations;
    }

    /**
     * Verifies access and reserves one daily AI request atomically.
     * Throws {@link AiDisabledException} or {@link AiQuotaExceededException}
     * if not. Otherwise returns the {@link TenantAiSettings} (for the
     * caller to read the {@code defaultModel}).
     *
     * <p>The tenant id is read from {@link TenantContext} (set by
     * {@code TenantInterceptor} from the JWT). AI quota tables are keyed by
     * {@code tenants.public_uuid}, so this method temporarily rebinds the
     * tenant context to that key for Hibernate {@code @TenantId} filtering.</p>
     */
    public TenantAiSettings verifyCanCall() {
        return verifyCanCall(AiEntitlementService.GENERATE);
    }

    public TenantAiSettings verifyCanCall(String feature) {
        pending.remove();
        UUID internalTenantId = TenantContext.currentRequired();
        AiEntitlementService.EffectiveAiPlan plan = entitlementService.require(feature);
        UUID aiTenantKey = aiTenantKeyResolver.resolve(internalTenantId);

        TenantAiSettings settings = settingsRepo.findActiveByTenantId(aiTenantKey).orElse(null);
        if (settings == null) {
            throw new AiDisabledException();
        }
        if (!settings.isAiEnabled()) {
            throw new AiDisabledException();
        }

        int dailyLimit = settings.getDailyRequestQuota() == null
                ? plan.dailyRequestLimit()
                : Math.min(settings.getDailyRequestQuota(), plan.dailyRequestLimit());
        long monthlyLimit = settings.getMonthlyTokenQuota() == null
                ? plan.monthlyTokenLimit()
                : Math.min(settings.getMonthlyTokenQuota(), plan.monthlyTokenLimit());
        reservationService.reserve(aiTenantKey, dailyLimit, monthlyLimit);
        pending.set(new Pending(aiTenantKey, monthlyLimit, null));
        return settings;
    }

    /** Reserve a conservative upper bound before the provider receives the request. */
    public void reserveForRequest(LlmRequest request) {
        Pending current = pending.get();
        if (current == null || current.reservationId() != null) {
            throw new IllegalStateException("AI quota must be verified once before reserving tokens");
        }
        long bound = request.systemPrompt().getBytes(StandardCharsets.UTF_8).length
                + request.userPrompt().getBytes(StandardCharsets.UTF_8).length
                + (request.maxTokens() == null ? 8192L : request.maxTokens()) + 512L;
        if (request.history() != null) {
            for (var item : request.history()) {
                bound += item.content().getBytes(StandardCharsets.UTF_8).length + 32L;
            }
        }
        UUID id = monthlyReservations.reserve(current.tenantId(), current.monthlyLimit(), bound);
        pending.set(new Pending(current.tenantId(), current.monthlyLimit(), id));
    }

    /** Transfers a caller-thread reservation to an async worker. */
    public UUID detachReservation() {
        Pending current = pending.get();
        pending.remove();
        if (current == null || current.reservationId() == null) {
            throw new IllegalStateException("No AI token reservation to dispatch");
        }
        return current.reservationId();
    }

    public void bindReservation(UUID reservationId) {
        UUID tenantId = aiTenantKeyResolver.resolve(TenantContext.currentRequired());
        pending.set(new Pending(tenantId, 0, reservationId));
    }

    public void clearReservation() {
        pending.remove();
    }

    /**
     * Atomically increments the daily counters for the current tenant.
     * Called by the {@code LmsAiService} after the LLM call resolves
     * (success or failure). Both success and failure are counted as a
     * request; only successful LLM responses add to {@code success_count}
     * and the token totals.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementCounters(boolean success, long tokensIn, long tokensOut) {
        Pending current = pending.get();
        pending.remove();
        if (current == null) {
            throw new IllegalStateException("AI quota was already settled or never reserved");
        }
        UUID internalTenantId = TenantContext.currentRequired();
        UUID aiTenantKey = aiTenantKeyResolver.resolve(internalTenantId);
        if (!aiTenantKey.equals(current.tenantId())) {
            throw new IllegalStateException("AI reservation tenant mismatch");
        }
        if (current.reservationId() != null) {
            if (!monthlyReservations.settle(aiTenantKey, current.reservationId(),
                    success, tokensIn, tokensOut)) {
                log.warn("AI token reservation already settled or absent tenant={} reservation={}",
                        aiTenantKey, current.reservationId());
            }
            return;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int successDelta = success ? 1 : 0;
        int failedDelta  = success ? 0 : 1;
        long tokensInDelta  = Math.max(0, tokensIn);
        long tokensOutDelta = Math.max(0, tokensOut);
        usageRepo.incrementCounters(aiTenantKey, today, 0, successDelta, failedDelta,
                tokensInDelta, tokensOutDelta);
        log.debug("Incremented AI usage for tenant={} day={} success={} tokensIn={} tokensOut={}",
                aiTenantKey, today, success, tokensIn, tokensOut);
    }

    private record Pending(UUID tenantId, long monthlyLimit, UUID reservationId) {}
}
