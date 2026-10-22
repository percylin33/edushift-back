package com.edushift.modules.ai.service;

import com.edushift.modules.ai.exception.AiQuotaExceededException;
import com.edushift.modules.ai.repository.TenantAiUsageRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable budget holds for concurrent requests; all queries use explicit tenant keys. */
@Service
@RequiredArgsConstructor
public class AiMonthlyTokenReservationService {

    private static final Logger log = LoggerFactory.getLogger(AiMonthlyTokenReservationService.class);
    private final JdbcTemplate jdbc;
    private final TenantAiUsageRepository usage;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID reserve(UUID tenantId, long monthlyLimit, long tokenBound) {
        lockTenant(tenantId);
        long spent = usage.sumTokensThisMonth(tenantId);
        Long held = jdbc.queryForObject("""
                SELECT COALESCE(SUM(reserved_tokens), 0)
                  FROM edushift.ai_token_reservations
                 WHERE tenant_id = ? AND status = 'RESERVED' AND expires_at > now()
                   AND usage_day >= date_trunc('month', now() AT TIME ZONE 'UTC')::date
                   AND usage_day < date_trunc('month', now() AT TIME ZONE 'UTC')::date + interval '1 month'
                """, Long.class, tenantId);
        if (tokenBound <= 0 || spent + held > monthlyLimit - tokenBound) {
            throw new AiQuotaExceededException(
                    "Monthly AI token quota cannot cover this request ("
                            + monthlyLimit + " tokens/month). The quota resets next month UTC.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO edushift.ai_token_reservations
                    (id, tenant_id, usage_day, reserved_tokens, expires_at)
                VALUES (?, ?, ?, ?, now() + interval '2 hours')
                """, id, tenantId, LocalDate.now(ZoneOffset.UTC), tokenBound);
        return id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean settle(UUID tenantId, UUID reservationId,
                          boolean success, long tokensIn, long tokensOut) {
        lockTenant(tenantId);
        long actual = Math.max(0, tokensIn) + Math.max(0, tokensOut);
        var days = jdbc.query("""
                UPDATE edushift.ai_token_reservations
                   SET status = 'SETTLED', actual_tokens = ?, updated_at = now()
                 WHERE id = ? AND tenant_id = ? AND status IN ('RESERVED','EXPIRED')
                RETURNING usage_day, reserved_tokens
                """, (rs, row) -> new Settlement(
                rs.getDate("usage_day").toLocalDate(), rs.getLong("reserved_tokens")),
                actual, reservationId, tenantId);
        if (days.isEmpty()) return false;
        Settlement settlement = days.getFirst();
        if (actual > settlement.reservedTokens()) {
            log.error("AI provider usage exceeded reserved bound tenant={} reservation={} actual={} bound={}",
                    tenantId, reservationId, actual, settlement.reservedTokens());
        }
        usage.incrementCounters(tenantId, settlement.usageDay(), 0,
                success ? 1 : 0, success ? 0 : 1,
                Math.max(0, tokensIn), Math.max(0, tokensOut));
        return true;
    }

    @Transactional(readOnly = true)
    public long heldThisMonth(UUID tenantId) {
        Long held = jdbc.queryForObject("""
                SELECT COALESCE(SUM(reserved_tokens), 0)
                  FROM edushift.ai_token_reservations
                 WHERE tenant_id = ? AND status = 'RESERVED' AND expires_at > now()
                   AND usage_day >= date_trunc('month', now() AT TIME ZONE 'UTC')::date
                   AND usage_day < date_trunc('month', now() AT TIME ZONE 'UTC')::date + interval '1 month'
                """, Long.class, tenantId);
        return held == null ? 0 : held;
    }

    @Scheduled(fixedDelayString = "${app.ai.token-reservation-cleanup-ms:3600000}")
    public void expireAbandoned() {
        jdbc.update("""
                UPDATE edushift.ai_token_reservations
                   SET status = 'EXPIRED', updated_at = now()
                 WHERE status = 'RESERVED' AND expires_at < now()
                """);
    }

    private void lockTenant(UUID tenantId) {
        jdbc.queryForObject(
                "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))) q",
                Integer.class, tenantId.toString());
    }

    private record Settlement(LocalDate usageDay, long reservedTokens) {}
}
