package com.edushift.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.entity.TenantAiUsage;
import com.edushift.modules.ai.exception.AiDisabledException;
import com.edushift.modules.ai.exception.AiQuotaExceededException;
import com.edushift.modules.ai.repository.TenantAiSettingsRepository;
import com.edushift.modules.ai.repository.TenantAiUsageRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AiQuotaService} (BE-7c.1).
 *
 * <p>The quota gates are pure (no I/O — everything is mocked), so
 * we exhaustively cover the master switch, daily quota, monthly
 * token quota, and the counter increment path.</p>
 */
class AiQuotaServiceTest {

    private TenantAiSettingsRepository settingsRepo;
    private TenantAiUsageRepository usageRepo;
    private AiTenantKeyResolver aiTenantKeyResolver;
    private AiEntitlementService entitlementService;
    private AiDailyQuotaReservationService reservationService;
    private AiMonthlyTokenReservationService monthlyReservations;
    private AiQuotaService service;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        settingsRepo = mock(TenantAiSettingsRepository.class);
        usageRepo = mock(TenantAiUsageRepository.class);
        aiTenantKeyResolver = mock(AiTenantKeyResolver.class);
        entitlementService = mock(AiEntitlementService.class);
        reservationService = mock(AiDailyQuotaReservationService.class);
        monthlyReservations = mock(AiMonthlyTokenReservationService.class);
        when(aiTenantKeyResolver.resolve(TENANT_ID)).thenReturn(TENANT_ID);
        when(entitlementService.require(AiEntitlementService.GENERATE)).thenReturn(
                new AiEntitlementService.EffectiveAiPlan(
                        "PRO", List.of(AiEntitlementService.GENERATE),
                        10_000, 10_000_000L, 20, 25, 100_000_000L, false));
        service = new AiQuotaService(settingsRepo, usageRepo, aiTenantKeyResolver,
                entitlementService, reservationService, monthlyReservations);
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------
    // verifyCanCall
    // -------------------------------------------------------------------

    @Test
    @DisplayName("no settings row → AiDisabledException")
    void noSettingsThrowsDisabled() {
        when(settingsRepo.findActiveByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyCanCall())
                .isInstanceOf(AiDisabledException.class);
    }

    @Test
    @DisplayName("ai_enabled=false → AiDisabledException")
    void disabledThrows() {
        when(settingsRepo.findActiveByTenantId(TENANT_ID)).thenReturn(Optional.of(
                settings(false, null, null, null)));

        assertThatThrownBy(() -> service.verifyCanCall())
                .isInstanceOf(AiDisabledException.class);
    }

    @Test
    @DisplayName("no daily quota, no monthly quota → passes")
    void unlimitedPasses() {
        TenantAiSettings s = settings(true, null, null, "openai/gpt-4o-mini");
        when(settingsRepo.findActiveByTenantId(TENANT_ID)).thenReturn(Optional.of(s));
        when(usageRepo.findByUsageDay(any(LocalDate.class))).thenReturn(Optional.empty());

        TenantAiSettings returned = service.verifyCanCall();
        assertThat(returned).isSameAs(s);
    }

    @Test
    @DisplayName("daily quota + current usage under limit → passes")
    void dailyUnderLimitPasses() {
        TenantAiSettings s = settings(true, 100, null, null);
        TenantAiUsage u = usage(50, 0, 0, 0L, 0L);
        when(settingsRepo.findActiveByTenantId(TENANT_ID)).thenReturn(Optional.of(s));
        when(usageRepo.findByUsageDay(any())).thenReturn(Optional.of(u));

        service.verifyCanCall();
    }

    @Test
    @DisplayName("daily quota is passed to the atomic reservation service")
    void dailyAtLimitThrows() {
        TenantAiSettings s = settings(true, 100, null, null);
        TenantAiUsage u = usage(100, 0, 0, 0L, 0L);
        when(settingsRepo.findActiveByTenantId(TENANT_ID)).thenReturn(Optional.of(s));
        when(usageRepo.findByUsageDay(any())).thenReturn(Optional.of(u));

        service.verifyCanCall();
        verify(reservationService).reserve(TENANT_ID, 100, 10_000_000L);
    }

    @Test
    @DisplayName("monthly token quota + tokens under limit → passes")
    void monthlyTokensUnderLimitPasses() {
        TenantAiSettings s = settings(true, null, 1_000_000L, null);
        when(settingsRepo.findActiveByTenantId(TENANT_ID)).thenReturn(Optional.of(s));
        when(usageRepo.findByUsageDay(any())).thenReturn(Optional.empty());
        when(usageRepo.sumTokensThisMonth(TENANT_ID)).thenReturn(500_000L);

        service.verifyCanCall();
    }

    @Test
    @DisplayName("monthly token quota is passed to the atomic reservation service")
    void monthlyTokensAtLimitThrows() {
        TenantAiSettings s = settings(true, null, 1_000_000L, null);
        when(settingsRepo.findActiveByTenantId(TENANT_ID)).thenReturn(Optional.of(s));
        when(usageRepo.findByUsageDay(any())).thenReturn(Optional.empty());
        when(usageRepo.sumTokensThisMonth(TENANT_ID)).thenReturn(1_000_000L);

        service.verifyCanCall();
        verify(reservationService).reserve(TENANT_ID, 10_000, 1_000_000L);
    }

    // -------------------------------------------------------------------
    // Multi-tenant isolation (bug fix: post-Sprint 8)
    // -------------------------------------------------------------------

    @Test
    @DisplayName("multi-tenant: queries the repo with the current tenant id, not a singleton")
    void queriesSettingsByCurrentTenant() {
        TenantAiSettings s = settings(true, null, null, null);
        when(settingsRepo.findActiveByTenantId(TENANT_ID)).thenReturn(Optional.of(s));
        when(usageRepo.findByUsageDay(any())).thenReturn(Optional.empty());

        service.verifyCanCall();

        verify(settingsRepo).findActiveByTenantId(TENANT_ID);
        verify(settingsRepo, never()).findActiveByTenantId(OTHER_TENANT_ID);
    }

    @Test
    @DisplayName("multi-tenant: ai_enabled=false on another tenant does NOT affect the current one")
    void crossTenantDisabledDoesNotLeak() {
        // other tenant has AI disabled
        when(settingsRepo.findActiveByTenantId(OTHER_TENANT_ID))
                .thenReturn(Optional.of(settings(false, null, null, null)));
        // current tenant has AI enabled
        TenantAiSettings s = settings(true, null, null, null);
        when(settingsRepo.findActiveByTenantId(TENANT_ID)).thenReturn(Optional.of(s));
        when(usageRepo.findByUsageDay(any())).thenReturn(Optional.empty());

        TenantAiSettings returned = service.verifyCanCall();

        assertThat(returned).isSameAs(s);
        // Defensive: must never look up by raw id either (would bypass tenant filter).
        verify(settingsRepo, never()).findById(any());
    }

    // -------------------------------------------------------------------
    // incrementCounters
    // -------------------------------------------------------------------

    @Test
    @DisplayName("incrementCounters(success) settles an already reserved request")
    void incrementCountersSuccess() {
        when(settingsRepo.findActiveByTenantId(TENANT_ID)).thenReturn(Optional.of(
                settings(true, null, null, null)));
        service.verifyCanCall();
        service.incrementCounters(true, 42L, 28L);

        verify(usageRepo).incrementCounters(
                TENANT_ID, LocalDate.now(ZoneOffset.UTC), 0, 1, 0, 42L, 28L);
    }

    @Test
    @DisplayName("incrementCounters(failure) settles an already reserved request")
    void incrementCountersFailure() {
        when(settingsRepo.findActiveByTenantId(TENANT_ID)).thenReturn(Optional.of(
                settings(true, null, null, null)));
        service.verifyCanCall();
        service.incrementCounters(false, 0L, 0L);

        verify(usageRepo).incrementCounters(
                TENANT_ID, LocalDate.now(ZoneOffset.UTC), 0, 0, 1, 0L, 0L);
    }

    // -------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------

    private static TenantAiSettings settings(boolean enabled, Integer daily, Long monthly, String model) {
        TenantAiSettings s = new TenantAiSettings();
        s.setAiEnabled(enabled);
        s.setDailyRequestQuota(daily);
        s.setMonthlyTokenQuota(monthly);
        s.setDefaultModel(model);
        return s;
    }

    private static TenantAiUsage usage(int req, int success, int failed, long tokIn, long tokOut) {
        TenantAiUsage u = new TenantAiUsage();
        u.setRequestCount(req);
        u.setSuccessCount(success);
        u.setFailedCount(failed);
        u.setTokensInTotal(tokIn);
        u.setTokensOutTotal(tokOut);
        return u;
    }
}
