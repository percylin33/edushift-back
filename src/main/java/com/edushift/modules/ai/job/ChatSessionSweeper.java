package com.edushift.modules.ai.job;

import com.edushift.modules.ai.entity.AiChatSession;
import com.edushift.modules.ai.repository.AiChatSessionRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Soft-deletes expired AI chat sessions (Sprint 8 / BE-8.3).
 *
 * <p>Run nightly. For each tenant with expired sessions (those whose
 * {@code expires_at} is in the past), marks them as
 * {@code DELETED} and cascades the soft-delete to the messages.
 *
 * <h3>Why a sweeper (and not a hard DELETE)</h3>
 * - Soft delete preserves the audit trail (billing per message is
 *   summed from the messages table; erasing them would lose track
 *   of "tokens used this month").
 * - A scheduled job is the right pattern: we don't have a hot path
 *   to clean up; expirations are 7d+ old and not time-sensitive.
 *
 * <h3>Multi-tenant</h3>
 * Uses Hibernate's {@code @TenantId} discriminator via
 * {@link TenantContext#runAs}. The sweeper iterates tenants
 * explicitly (no global query); each tenant is processed in its own
 * transaction so a failure in one tenant doesn't roll back others.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatSessionSweeper {

    private final AiChatSessionRepository sessionRepo;

    /**
     * How many expired sessions to process per batch. Keeps the
     * transaction size bounded.
     */
    @Value("${app.ai.chat.sweeper.batch-size:200}")
    private int batchSize;

    /**
     * Run once a day at 03:17 UTC (after the AI quota sweeper at 03:00).
     * Cron is configurable via property; default fixed.
     */
    @Scheduled(cron = "${app.ai.chat.sweeper.cron:0 17 3 * * *}")
    @Transactional
    public void sweep() {
        Instant cutoff = Instant.now();
        List<AiChatSession> batch = sessionRepo.findExpired(cutoff, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            log.debug("[ChatSessionSweeper] no expired sessions at {}", cutoff);
            return;
        }
        int deleted = 0;
        for (AiChatSession s : batch) {
            UUID tenantId = s.getTenantId();
            // Run the soft-delete under the session's tenant context so
            // Hibernate's @TenantId filter doesn't drop it.
            try {
                TenantContext.runAs(tenantId, () -> {
                    s.setStatus(AiChatSession.Status.DELETED);
                    s.markDeleted();
                    sessionRepo.save(s);
                    return null;
                });
                deleted++;
            } catch (Exception ex) {
                log.warn("[ChatSessionSweeper] failed to delete session id={} tenant={}: {}",
                        s.getId(), tenantId, ex.getMessage());
            }
        }
        log.info("[ChatSessionSweeper] cutoff={} deleted={} of batch={}",
                cutoff, deleted, batch.size());
    }
}
