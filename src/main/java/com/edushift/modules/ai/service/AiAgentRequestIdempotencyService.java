package com.edushift.modules.ai.service;

import com.edushift.modules.ai.safety.AuditHash;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Prevents repeated agent POSTs from charging or appending duplicate answers. */
@Service
@RequiredArgsConstructor
public class AiAgentRequestIdempotencyService {

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(UUID sessionId, UUID userId, String key, String input) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,100}")) {
            throw new BadRequestException("AI_IDEMPOTENCY_KEY_INVALID",
                    "Se requiere una clave de solicitud válida.");
        }
        UUID tenantId = TenantContext.currentRequired();
        String hash = AuditHash.sha256Hex(input);
        int inserted = jdbc.update("""
                INSERT INTO edushift.ai_agent_request_keys
                    (id, tenant_id, session_id, user_id, idempotency_key, input_hash)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, session_id, user_id, idempotency_key) DO NOTHING
                """, UUID.randomUUID(), tenantId, sessionId, userId, key, hash);
        if (inserted == 1) return new Claim(State.NEW, null);
        Claim existing = jdbc.queryForObject("""
                SELECT input_hash, status, assistant_public_uuid
                  FROM edushift.ai_agent_request_keys
                 WHERE tenant_id = ? AND session_id = ? AND user_id = ?
                   AND idempotency_key = ? AND deleted = false
                """, (rs, row) -> {
            if (!hash.equals(rs.getString("input_hash").trim())) {
                throw new ConflictException("AI_IDEMPOTENCY_KEY_REUSED",
                        "La clave ya pertenece a otra pregunta.");
            }
            return new Claim(State.valueOf(rs.getString("status")),
                    rs.getObject("assistant_public_uuid", UUID.class));
        }, tenantId, sessionId, userId, key);
        if (existing == null) throw new IllegalStateException("AI request key disappeared");
        return existing;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID sessionId, UUID userId, String key, UUID assistantId) {
        update(sessionId, userId, key, "COMPLETED", assistantId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID sessionId, UUID userId, String key) {
        update(sessionId, userId, key, "FAILED", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Scheduled(fixedDelayString = "${app.ai.idempotency-cleanup-ms:3600000}")
    public void expireStale() {
        jdbc.update("""
                UPDATE edushift.ai_agent_request_keys
                   SET status = 'FAILED', updated_at = now()
                 WHERE status = 'PROCESSING'
                   AND created_at < now() - interval '2 hours'
                """);
    }

    private void update(UUID sessionId, UUID userId, String key,
                        String status, UUID assistantId) {
        jdbc.update("""
                UPDATE edushift.ai_agent_request_keys
                   SET status = ?, assistant_public_uuid = ?, updated_at = now()
                 WHERE tenant_id = ? AND session_id = ? AND user_id = ?
                   AND idempotency_key = ? AND status = 'PROCESSING'
                """, status, assistantId, TenantContext.currentRequired(),
                sessionId, userId, key);
    }

    public enum State { NEW, PROCESSING, COMPLETED, FAILED }
    public record Claim(State state, UUID assistantPublicUuid) {}
}
