package com.edushift.modules.notifications.service;

import com.edushift.modules.notifications.entity.Announcement;
import com.edushift.modules.notifications.entity.Announcement.AudienceType;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves an announcement's audience to a concrete set of user IDs
 * (Sprint 9 / BE-9.4).
 *
 * <p>Strategy:</p>
 * <ul>
 *   <li><b>SCHOOL</b> — all users in the current tenant.</li>
 *   <li><b>GRADE</b> — students in any section of the given grades.</li>
 *   <li><b>SECTION</b> — students + teachers of the given sections.</li>
 *   <li><b>COURSE</b> — teacher(s) of the course + students enrolled.</li>
 *   <li><b>ROLE</b> — users with the given role (e.g. {@code TEACHER}).</li>
 *   <li><b>USER</b> — explicit user ids (no resolution needed).</li>
 * </ul>
 *
 * <h3>Why JdbcTemplate</h3>
 * Audience resolution is read-only and joins 3-4 tables. Native SQL
 * with JdbcTemplate is clearer than a JPQL with subqueries; tenant
 * isolation is guaranteed by the {@code tenant_id} parameter
 * (anti-enumeration).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnnouncementAudienceResolver {

    private final DataSource dataSource;

    public List<UUID> resolve(Announcement a) {
        UUID tenantId = TenantContext.currentRequired();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return switch (a.getAudienceType()) {
            case SCHOOL -> jdbc.queryForList(
                    "SELECT id FROM edushift.users WHERE tenant_id = ? AND deleted = false",
                    UUID.class, tenantId);
            case USER -> {
                List<UUID> ids = new ArrayList<>();
                for (String s : a.getAudienceIds()) {
                    try { ids.add(UUID.fromString(s)); } catch (Exception ignored) {}
                }
                yield ids;
            }
            case ROLE -> {
                List<String> roleNames = a.getAudienceIds();
                if (roleNames.isEmpty()) yield List.of();
                String inClause = String.join(",", roleNames.stream()
                        .map(r -> "'" + r.replace("'", "''") + "'")
                        .toList());
                yield jdbc.queryForList(
                        "SELECT DISTINCT u.id FROM edushift.users u " +
                        "JOIN edushift.user_roles ur ON ur.user_id = u.id " +
                        "JOIN edushift.roles r ON r.id = ur.role_id " +
                        "WHERE u.tenant_id = ? AND r.name IN (" + inClause + ") " +
                        "  AND u.deleted = false AND r.deleted = false",
                        UUID.class, tenantId);
            }
            case GRADE -> {
                List<String> ids = a.getAudienceIds();
                if (ids.isEmpty()) yield List.of();
                yield jdbc.queryForList(
                        "SELECT DISTINCT s.user_id FROM edushift.students s " +
                        "WHERE s.tenant_id = ? AND s.grade_code IN (" +
                        ids.stream().map(g -> "'" + g.replace("'", "''") + "'")
                                .reduce((x, y) -> x + "," + y).orElse("''") +
                        ") AND s.deleted = false",
                        UUID.class, tenantId);
            }
            case SECTION -> {
                List<UUID> ids = parseUuids(a.getAudienceIds());
                if (ids.isEmpty()) yield List.of();
                yield jdbc.queryForList(
                        "SELECT DISTINCT user_id FROM (" +
                        "  SELECT s.user_id AS user_id FROM edushift.students s " +
                        "   WHERE s.tenant_id = ? AND s.section_id = ANY(?) AND s.deleted = false " +
                        "  UNION " +
                        "  SELECT t.user_id AS user_id FROM edushift.teachers t " +
                        "   WHERE t.tenant_id = ? AND t.section_id = ANY(?) AND t.deleted = false" +
                        ") AS u",
                        UUID.class, tenantId, ids.toArray(), tenantId, ids.toArray());
            }
            case COURSE -> {
                List<UUID> ids = parseUuids(a.getAudienceIds());
                if (ids.isEmpty()) yield List.of();
                yield jdbc.queryForList(
                        "SELECT DISTINCT user_id FROM (" +
                        "  SELECT t.user_id AS user_id FROM edushift.teachers t " +
                        "   WHERE t.tenant_id = ? AND t.course_id = ANY(?) AND t.deleted = false " +
                        "  UNION " +
                        "  SELECT s.user_id AS user_id FROM edushift.students s " +
                        "   JOIN edushift.enrollments e ON e.student_id = s.id " +
                        "   WHERE e.tenant_id = ? AND e.course_id = ANY(?) AND s.deleted = false" +
                        ") AS u",
                        UUID.class, tenantId, ids.toArray(), tenantId, ids.toArray());
            }
        };
    }

    private static List<UUID> parseUuids(List<String> raw) {
        List<UUID> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            try { out.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        return out;
    }
}
