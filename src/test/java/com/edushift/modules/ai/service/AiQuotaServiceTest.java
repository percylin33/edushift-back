package com.edushift.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private AiQuotaService service;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        settingsRepo = mock(TenantAiSettingsRepository.class);
        usageRepo = mock(TenantAiUsageRepository.class);
        service = new AiQuotaService(settingsRepo, usageRepo);
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
        when(settingsRepo.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyCanCall())
                .isInstanceOf(AiDisabledException.class);
    }

    @Test
    @DisplayName("ai_enabled=false → AiDisabledException")
    void disabledThrows() {
        when(settingsRepo.findFirstByOrderByIdAsc()).thenReturn(Optional.of(
                settings(false, null, null, null)));

        assertThatThrownBy(() -> service.verifyCanCall())
                .isInstanceOf(AiDisabledException.class);
    }

    @Test
    @DisplayName("no daily quota, no monthly quota → passes")
    void unlimitedPasses() {
        TenantAiSettings s = settings(true, null, null, "openai/gpt-4o-mini");
        when(settingsRepo.findFirstByOrderByIdAsc()).thenReturn(Optional.of(s));
        when(usageRepo.findByUsageDay(any(LocalDate.class))).thenReturn(Optional.empty());

        TenantAiSettings returned = service.verifyCanCall();
        assertThat(returned).isSameAs(s);
    }

    @Test
    @DisplayName("daily quota + current usage under limit → passes")
    void dailyUnderLimitPasses() {
        TenantAiSettings s = settings(true, 100, null, null);
        TenantAiUsage u = usage(50, 0, 0, 0L, 0L);
        when(settingsRepo.findFirstByOrderByIdAsc()).thenReturn(Optional.of(s));
        when(usageRepo.findByUsageDay(any())).thenReturn(Optional.of(u));

        service.verifyCanCall();
    }

    @Test
    @DisplayName("daily quota + current usage at limit → AiQuotaExceededException (daily)")
    void dailyAtLimitThrows() {
        TenantAiSettings s = settings(true, 100, null, null);
        TenantAiUsage u = usage(100, 0, 0, 0L, 0L);
        when(settingsRepo.findFirstByOrderByIdAsc()).thenReturn(Optional.of(s));
        when(usageRepo.findByUsageDay(any())).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.verifyCanCall())
                .isInstanceOf(AiQuotaExceededException.class)
                .hasMessageContaining("Daily");
    }

    @Test
    @DisplayName("monthly token quota + tokens under limit → passes")
    void monthlyTokensUnderLimitPasses() {
        TenantAiSettings s = settings(true, null, 1_000_000L, null);
        when(settingsRepo.findFirstByOrderByIdAsc()).thenReturn(Optional.of(s));
        when(usageRepo.findByUsageDay(any())).thenReturn(Optional.empty());
        when(usageRepo.sumTokensThisMonth(TENANT_ID)).thenReturn(500_000L);

        service.verifyCanCall();
    }

    @Test
    @DisplayName("monthly token quota + tokens at limit → AiQuotaExceededException (monthly)")
    void monthlyTokensAtLimitThrows() {
        TenantAiSettings s = settings(true, null, 1_000_000L, null);
        when(settingsRepo.findFirstByOrderByIdAsc()).thenReturn(Optional.of(s));
        when(usageRepo.findByUsageDay(any())).thenReturn(Optional.empty());
        when(usageRepo.sumTokensThisMonth(TENANT_ID)).thenReturn(1_000_000L);

        assertThatThrownBy(() -> service.verifyCanCall())
                .isInstanceOf(AiQuotaExceededException.class)
                .hasMessageContaining("Monthly");
    }

    // -------------------------------------------------------------------
    // incrementCounters
    // -------------------------------------------------------------------

    @Test
    @DisplayName("incrementCounters(success) → +1 request, +1 success, token counts")
    void incrementCountersSuccess() {
        service.incrementCounters(true, 42L, 28L);

        verify(usageRepo).incrementCounters(
                TENANT_ID, LocalDate.now(), 1, 1, 0, 42L, 28L);
    }

    @Test
    @DisplayName("incrementCounters(failure) → +1 request, +1 failed, 0 tokens")
    void incrementCountersFailure() {
        service.incrementCounters(false, 0L, 0L);

        verify(usageRepo).incrementCounters(
                TENANT_ID, LocalDate.now(), 1, 0, 1, 0L, 0L);
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
