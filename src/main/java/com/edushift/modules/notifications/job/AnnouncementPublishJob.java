package com.edushift.modules.notifications.job;

import com.edushift.modules.notifications.service.AnnouncementService;
import com.edushift.shared.multitenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes announcements whose {@code publish_at} has passed.
 * Uses JDBC so the scan is not blocked by the Hibernate tenant filter;
 * each row is then published inside that tenant's context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementPublishJob {

    private final DataSource dataSource;
    private final AnnouncementService announcementService;

    @Scheduled(fixedDelayString = "${app.announcements.publish-interval-ms:60000}",
            initialDelay = 20_000)
    public void publishDue() {
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        List<Due> due = new JdbcTemplate(dataSource).query("""
                SELECT public_uuid, tenant_id
                FROM edushift.announcements
                WHERE status = 'SCHEDULED'
                  AND publish_at IS NOT NULL
                  AND publish_at <= ?
                  AND deleted = false
                ORDER BY publish_at ASC
                LIMIT 50
                """,
                ps -> ps.setObject(1, now, Types.TIMESTAMP_WITH_TIMEZONE),
                this::mapDue);
        if (due.isEmpty()) return;
        log.info("[Announcement] scheduled publish batch size={}", due.size());
        for (Due row : due) {
            try {
                TenantContext.runAs(row.tenantId(), () -> {
                    announcementService.publishScheduled(row.publicUuid());
                    return Boolean.TRUE;
                });
            } catch (RuntimeException ex) {
                log.warn("[Announcement] scheduled publish failed publicUuid={}: {}",
                        row.publicUuid(), ex.getMessage());
            }
        }
    }

    private Due mapDue(ResultSet rs, int rowNum) throws SQLException {
        return new Due(rs.getObject("public_uuid", UUID.class), rs.getObject("tenant_id", UUID.class));
    }

    private record Due(UUID publicUuid, UUID tenantId) {}
}
