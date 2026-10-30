package com.edushift.modules.ai.service;

import com.edushift.modules.ai.exception.AiQuotaExceededException;
import com.edushift.shared.multitenancy.TenantContext;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Per-student tutor allowance; lock is held by the message transaction. */
@Service
@RequiredArgsConstructor
public class AiTutorDailyLimitService {

    private final JdbcTemplate jdbc;
    private final AiEntitlementService entitlements;

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAvailable(UUID studentPublicUuid) {
        UUID tenantId = TenantContext.currentRequired();
        int limit = entitlements.require(AiEntitlementService.TUTOR)
                .tutorMessagesPerStudentDay();
        String lockKey = tenantId + ":tutor:" + studentPublicUuid;
        jdbc.queryForObject(
                "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))) q",
                Integer.class, lockKey);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Long used = jdbc.queryForObject("""
                SELECT count(*)
                FROM edushift.ai_chat_messages m
                JOIN edushift.ai_chat_sessions s
                  ON s.id = m.chat_session_id AND s.tenant_id = m.tenant_id
                WHERE m.tenant_id = ? AND s.tenant_id = ?
                  AND s.subject_public_uuid = ? AND s.agent_type = 'TUTOR'
                  AND m.role = 'user' AND m.deleted = false AND s.deleted = false
                  AND m.created_at >= ? AND m.created_at < ?
                """, Long.class, tenantId, tenantId, studentPublicUuid,
                Timestamp.from(today.atStartOfDay(ZoneOffset.UTC).toInstant()),
                Timestamp.from(today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        if (used != null && used >= limit) {
            throw new AiQuotaExceededException(
                    "Límite diario de mensajes del tutor alcanzado para este estudiante.");
        }
    }
}
