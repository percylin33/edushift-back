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
 * <h3>Critical: returned UUIDs are {@code users.public_uuid}, not {@code users.id}</h3>
 *
 * <p>Per V48 (and V29/V30 before it), every FK on a "user identifier"
 * column in the EduShift schema points at {@code users.public_uuid}
 * (the external UUIDv4 surfaced to clients), NOT at the internal
 * {@code users.id} (UUIDv7 PK). The runtime contract — see
 * {@code JwtAuthenticatedPrincipal} — feeds user identifier columns
 * with the {@code publicUuid} from the JWT {@code sub} claim.</p>
 *
 * <p>V75 fixed the {@code announcement_recipients.user_id} FK to point
 * at {@code users.public_uuid}. Before V75/V48 the FK pointed at
 * {@code users.id}, so this resolver returned {@code users.id} values
 * and everything matched. After V48 the FK target changed but this
 * resolver was not updated — every INSERT into {@code
 * announcement_recipients} failed with {@code
 * DataIntegrityViolationException} because the resolver was returning
 * UUIDv7 values into a column whose FK targets UUIDv4.</p>
 *
 * <p>V76 + V77 (Phase 3 / DEBT-FK-BUGS-3 closure) extended the same
 * pattern to {@code students.user_id} and {@code teachers.user_id}.
 * Both columns now store {@code users.public_uuid} directly, so the
 * GRADE / SECTION / COURSE cases below select {@code s.user_id} /
 * {@code t.user_id} without the previous JOIN through {@code users}.
 * The cases that go through {@code enrollments} (COURSE) keep the
 * student-side JOIN because {@code enrollments.student_id} is the
 * internal PK of the students table — that one stays as-is.</p>
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
                    "SELECT public_uuid FROM edushift.users "
                    + "WHERE tenant_id = ? AND deleted = false",
                    UUID.class, tenantId);
            case USER -> {
                // The FE sends users.publicUuid values (what the JWT
                // sub claim carries) — no translation needed.
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
                        "SELECT DISTINCT u.public_uuid FROM edushift.users u "
                        + "JOIN edushift.user_roles ur ON ur.user_id = u.id "
                        + "JOIN edushift.roles r ON r.id = ur.role_id "
                        + "WHERE u.tenant_id = ? AND r.name IN (" + inClause + ") "
                        + "  AND u.deleted = false AND r.deleted = false",
                        UUID.class, tenantId);
            }
            case GRADE -> {
                List<String> ids = a.getAudienceIds();
                if (ids.isEmpty()) yield List.of();
                // DEBT-FK-BUGS-3 / V76: students.user_id FK now points at
                // users.public_uuid, so the JOIN through users is no
                // longer needed. The path students → student_enrollments
                // → sections → grades replaces the V75 attempt to read
                // a non-existent `s.grade_code` column directly.
                // Manual tenant_id filters are required because
                // JdbcTemplate bypasses Hibernate's @TenantId discriminator.
                yield jdbc.queryForList(
                        "SELECT DISTINCT s.user_id FROM edushift.students s "
                        + "JOIN edushift.student_enrollments se "
                        + "  ON se.student_id = s.id "
                        + "JOIN edushift.sections sec "
                        + "  ON sec.id = se.section_id "
                        + "JOIN edushift.grades g "
                        + "  ON g.id = sec.grade_id "
                        + "WHERE g.tenant_id = ? AND s.tenant_id = ? "
                        + "  AND se.tenant_id = ? AND sec.tenant_id = ? "
                        + "  AND g.name IN ("
                        + ids.stream().map(c -> "'" + c.replace("'", "''") + "'")
                                .reduce((x, y) -> x + "," + y).orElse("''")
                        + ") AND se.deleted = false AND se.status = 'ACTIVE' "
                        + "  AND sec.deleted = false AND s.deleted = false "
                        + "  AND s.user_id IS NOT NULL",
                        UUID.class, tenantId, tenantId, tenantId, tenantId);
            }
            case SECTION -> {
                List<UUID> ids = parseUuids(a.getAudienceIds());
                if (ids.isEmpty()) yield List.of();
                // Students are linked to sections through
                // student_enrollments (per Sprint 4 / BE-4.8); teachers
                // are linked through teacher_assignments (BE-4.7).
                // Neither table has a direct section_id on students /
                // teachers — the V75 fix used `s.section_id = ANY(?)`
                // which was a bug (column does not exist). This is
                // the correct path via the enrollment / assignment
                // pivot tables. Manual tenant_id filters required for
                // raw JdbcTemplate.
                yield jdbc.queryForList(
                        "SELECT DISTINCT user_id FROM ("
                        + "  SELECT s.user_id FROM edushift.students s "
                        + "   JOIN edushift.student_enrollments se "
                        + "     ON se.student_id = s.id "
                        + "   WHERE s.tenant_id = ? AND se.tenant_id = ? "
                        + "     AND se.section_id = ANY(?) "
                        + "     AND se.deleted = false "
                        + "     AND se.status = 'ACTIVE' "
                        + "     AND s.deleted = false AND s.user_id IS NOT NULL "
                        + "  UNION "
                        + "  SELECT t.user_id FROM edushift.teachers t "
                        + "   JOIN edushift.teacher_assignments ta "
                        + "     ON ta.teacher_id = t.id "
                        + "   WHERE t.tenant_id = ? AND ta.tenant_id = ? "
                        + "     AND ta.section_id = ANY(?) "
                        + "     AND ta.deleted = false "
                        + "     AND ta.unassigned_at IS NULL "
                        + "     AND t.deleted = false AND t.user_id IS NOT NULL"
                        + ") AS u",
                        UUID.class,
                        tenantId, tenantId, ids.toArray(),
                        tenantId, tenantId, ids.toArray());
            }
            case COURSE -> {
                List<UUID> ids = parseUuids(a.getAudienceIds());
                if (ids.isEmpty()) yield List.of();
                // Course audience = teachers assigned to that course
                // (via teacher_assignments.course_id). Students are
                // enrolled in sections, not courses directly, so a
                // COURSE announcement targets the teaching staff. If
                // we ever need students-of-course, we extend with a
                // JOIN through student_enrollments × teacher_assignments.
                yield jdbc.queryForList(
                        "SELECT DISTINCT t.user_id FROM edushift.teachers t "
                        + "JOIN edushift.teacher_assignments ta "
                        + "  ON ta.teacher_id = t.id "
                        + "WHERE t.tenant_id = ? AND ta.tenant_id = ? "
                        + "  AND ta.course_id = ANY(?) "
                        + "  AND ta.deleted = false "
                        + "  AND ta.unassigned_at IS NULL "
                        + "  AND t.deleted = false "
                        + "  AND t.user_id IS NOT NULL",
                        UUID.class, tenantId, tenantId, ids.toArray());
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