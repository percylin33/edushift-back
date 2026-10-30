package com.edushift.modules.notifications.service;

import com.edushift.modules.notifications.dto.TeachingScopeResponse.Option;
import com.edushift.modules.notifications.entity.Announcement;
import com.edushift.modules.notifications.entity.Announcement.AudienceType;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves an announcement's audience to concrete {@code users.public_uuid}
 * values. JdbcTemplate is used because the joins cross several modules and
 * must carry an explicit {@code tenant_id} predicate (JdbcTemplate bypasses
 * Hibernate {@code @TenantId}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnnouncementAudienceResolver {

    private final DataSource dataSource;

    public List<UUID> resolve(Announcement a) {
        return resolveDetailed(a).distinct();
    }

    public AudienceBreakdown resolveDetailed(Announcement a) {
        UUID tenantId = TenantContext.currentRequired();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String> rawIds = a.getAudienceIds() == null ? List.of() : a.getAudienceIds();
        return switch (a.getAudienceType()) {
            case SCHOOL -> bucketByRole(jdbc, """
                    SELECT DISTINCT u.public_uuid, user_role.name
                    FROM edushift.users u
                    LEFT JOIN LATERAL unnest(u.roles) AS user_role(name) ON true
                    WHERE u.tenant_id = ? AND u.deleted = false
                    """, tenantId);
            case USER -> {
                List<UUID> ids = parseUuids(rawIds);
                if (ids.isEmpty()) yield AudienceBreakdown.empty();
                yield bucketByRole(jdbc, """
                        SELECT DISTINCT u.public_uuid, user_role.name
                        FROM edushift.users u
                        LEFT JOIN LATERAL unnest(u.roles) AS user_role(name) ON true
                        WHERE u.tenant_id = ? AND u.deleted = false
                          AND u.public_uuid = ANY(?)
                        """, tenantId, ids.toArray(UUID[]::new));
            }
            case ROLE -> {
                if (rawIds.isEmpty()) yield AudienceBreakdown.empty();
                yield bucketByRole(jdbc, """
                        SELECT DISTINCT u.public_uuid, user_role.name
                        FROM edushift.users u
                        JOIN LATERAL unnest(u.roles) AS user_role(name) ON true
                        WHERE u.tenant_id = ? AND user_role.name = ANY(?)
                          AND u.deleted = false
                        """, tenantId, rawIds.toArray(String[]::new));
            }
            case GRADE -> resolveGrade(jdbc, tenantId, parseUuids(rawIds), a.isIncludeGuardians());
            case SECTION -> resolveSection(jdbc, tenantId, parseUuids(rawIds), a.isIncludeGuardians());
            case COURSE -> resolveCourse(jdbc, tenantId, parseUuids(rawIds), a.isIncludeGuardians());
        };
    }

    /**
     * Teachers may only target sections and courses they are actively assigned to.
     */
    public void assertTeacherAssigned(UUID teacherUserId, AudienceType type, List<String> rawIds) {
        if (type != AudienceType.SECTION && type != AudienceType.COURSE) {
            throw new ForbiddenException(
                    "ANNOUNCEMENT_AUDIENCE_FORBIDDEN",
                    "Teachers can only announce to their own sections or courses");
        }
        List<String> ids = rawIds == null ? List.of() : rawIds;
        List<UUID> parsed = parseUuids(ids);
        if (parsed.isEmpty() || parsed.size() != ids.size()) {
            throw new BusinessException(
                    "ANNOUNCEMENT_AUDIENCE_INVALID",
                    "Select at least one valid section or course");
        }
        UUID tenantId = TenantContext.currentRequired();
        String sql = type == AudienceType.SECTION
                ? """
                SELECT COUNT(DISTINCT sec.public_uuid) FROM edushift.teacher_assignments ta
                JOIN edushift.teachers t ON t.id = ta.teacher_id
                JOIN edushift.sections sec ON sec.id = ta.section_id
                WHERE t.tenant_id = ? AND ta.tenant_id = ? AND sec.tenant_id = ?
                  AND t.user_id = ? AND sec.public_uuid = ANY(?)
                  AND ta.deleted = false AND ta.unassigned_at IS NULL
                  AND sec.deleted = false AND t.deleted = false
                """
                : """
                SELECT COUNT(DISTINCT c.public_uuid) FROM edushift.teacher_assignments ta
                JOIN edushift.teachers t ON t.id = ta.teacher_id
                JOIN edushift.courses c ON c.id = ta.course_id
                WHERE t.tenant_id = ? AND ta.tenant_id = ? AND c.tenant_id = ?
                  AND t.user_id = ? AND c.public_uuid = ANY(?)
                  AND ta.deleted = false AND ta.unassigned_at IS NULL
                  AND c.deleted = false AND t.deleted = false
                """;
        Integer matched = new JdbcTemplate(dataSource).queryForObject(
                sql, Integer.class, tenantId, tenantId, tenantId, teacherUserId, parsed.toArray(UUID[]::new));
        if (matched == null || matched < parsed.size()) {
            throw new ForbiddenException(
                    "ANNOUNCEMENT_AUDIENCE_FORBIDDEN",
                    "You can only announce to sections or courses assigned to you");
        }
    }

    public List<Option> sectionsForTeacher(UUID teacherUserId) {
        UUID tenantId = TenantContext.currentRequired();
        return new JdbcTemplate(dataSource).query("""
                SELECT DISTINCT sec.public_uuid::text, sec.name, COALESCE(g.name, '')
                FROM edushift.teacher_assignments ta
                JOIN edushift.teachers t ON t.id = ta.teacher_id
                JOIN edushift.sections sec ON sec.id = ta.section_id
                LEFT JOIN edushift.grades g ON g.id = sec.grade_id
                WHERE t.tenant_id = ? AND ta.tenant_id = ? AND sec.tenant_id = ?
                  AND t.user_id = ?
                  AND ta.deleted = false AND ta.unassigned_at IS NULL
                  AND sec.deleted = false AND t.deleted = false
                ORDER BY sec.name
                """,
                (rs, i) -> new Option(rs.getString(1), rs.getString(2), rs.getString(3)),
                tenantId, tenantId, tenantId, teacherUserId);
    }

    public List<Option> coursesForTeacher(UUID teacherUserId) {
        UUID tenantId = TenantContext.currentRequired();
        return new JdbcTemplate(dataSource).query("""
                SELECT DISTINCT c.public_uuid::text, c.name, COALESCE(c.code, '')
                FROM edushift.teacher_assignments ta
                JOIN edushift.teachers t ON t.id = ta.teacher_id
                JOIN edushift.courses c ON c.id = ta.course_id
                WHERE t.tenant_id = ? AND ta.tenant_id = ? AND c.tenant_id = ?
                  AND t.user_id = ?
                  AND ta.deleted = false AND ta.unassigned_at IS NULL
                  AND c.deleted = false AND t.deleted = false
                ORDER BY c.name
                """,
                (rs, i) -> new Option(rs.getString(1), rs.getString(2), rs.getString(3)),
                tenantId, tenantId, tenantId, teacherUserId);
    }

    private AudienceBreakdown resolveGrade(JdbcTemplate jdbc, UUID tenantId, List<UUID> gradeIds, boolean guardians) {
        if (gradeIds.isEmpty()) return AudienceBreakdown.empty();
        UUID[] ids = gradeIds.toArray(UUID[]::new);
        Set<UUID> students = new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT s.user_id FROM edushift.students s
                JOIN edushift.student_enrollments se ON se.student_id = s.id
                JOIN edushift.sections sec ON sec.id = se.section_id
                JOIN edushift.grades g ON g.id = sec.grade_id
                WHERE g.tenant_id = ? AND s.tenant_id = ? AND se.tenant_id = ? AND sec.tenant_id = ?
                  AND g.public_uuid = ANY(?)
                  AND se.deleted = false AND se.status = 'ACTIVE'
                  AND sec.deleted = false AND s.deleted = false
                  AND s.user_id IS NOT NULL
                """, UUID.class, tenantId, tenantId, tenantId, tenantId, ids));
        Set<UUID> guardianIds = guardians
                ? guardiansOfGrade(jdbc, tenantId, ids)
                : Set.of();
        return new AudienceBreakdown(students, Set.of(), guardianIds, Set.of());
    }

    private AudienceBreakdown resolveSection(JdbcTemplate jdbc, UUID tenantId, List<UUID> sectionIds, boolean guardians) {
        if (sectionIds.isEmpty()) return AudienceBreakdown.empty();
        UUID[] ids = sectionIds.toArray(UUID[]::new);
        Set<UUID> students = new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT s.user_id FROM edushift.students s
                JOIN edushift.student_enrollments se ON se.student_id = s.id
                JOIN edushift.sections sec ON sec.id = se.section_id
                WHERE s.tenant_id = ? AND se.tenant_id = ? AND sec.tenant_id = ?
                  AND sec.public_uuid = ANY(?)
                  AND sec.deleted = false AND se.deleted = false AND se.status = 'ACTIVE'
                  AND s.deleted = false AND s.user_id IS NOT NULL
                """, UUID.class, tenantId, tenantId, tenantId, ids));
        Set<UUID> teachers = new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT t.user_id FROM edushift.teachers t
                JOIN edushift.teacher_assignments ta ON ta.teacher_id = t.id
                JOIN edushift.sections sec ON sec.id = ta.section_id
                WHERE t.tenant_id = ? AND ta.tenant_id = ? AND sec.tenant_id = ?
                  AND sec.public_uuid = ANY(?)
                  AND sec.deleted = false AND ta.deleted = false AND ta.unassigned_at IS NULL
                  AND t.deleted = false AND t.user_id IS NOT NULL
                """, UUID.class, tenantId, tenantId, tenantId, ids));
        Set<UUID> guardianIds = guardians ? guardiansOfSection(jdbc, tenantId, ids) : Set.of();
        return new AudienceBreakdown(students, teachers, guardianIds, Set.of());
    }

    private AudienceBreakdown resolveCourse(JdbcTemplate jdbc, UUID tenantId, List<UUID> courseIds, boolean guardians) {
        if (courseIds.isEmpty()) return AudienceBreakdown.empty();
        UUID[] ids = courseIds.toArray(UUID[]::new);
        Set<UUID> students = new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT s.user_id FROM edushift.students s
                JOIN edushift.student_enrollments se ON se.student_id = s.id
                JOIN edushift.sections sec ON sec.id = se.section_id
                JOIN edushift.teacher_assignments ta ON ta.section_id = sec.id
                JOIN edushift.courses c ON c.id = ta.course_id
                WHERE s.tenant_id = ? AND se.tenant_id = ? AND sec.tenant_id = ?
                  AND ta.tenant_id = ? AND c.tenant_id = ?
                  AND c.public_uuid = ANY(?)
                  AND c.deleted = false AND sec.deleted = false
                  AND se.deleted = false AND se.status = 'ACTIVE'
                  AND ta.deleted = false AND ta.unassigned_at IS NULL
                  AND s.deleted = false AND s.user_id IS NOT NULL
                """, UUID.class, tenantId, tenantId, tenantId, tenantId, tenantId, ids));
        Set<UUID> teachers = new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT t.user_id FROM edushift.teachers t
                JOIN edushift.teacher_assignments ta ON ta.teacher_id = t.id
                JOIN edushift.courses c ON c.id = ta.course_id
                WHERE t.tenant_id = ? AND ta.tenant_id = ? AND c.tenant_id = ?
                  AND c.public_uuid = ANY(?)
                  AND c.deleted = false AND ta.deleted = false AND ta.unassigned_at IS NULL
                  AND t.deleted = false AND t.user_id IS NOT NULL
                """, UUID.class, tenantId, tenantId, tenantId, ids));
        Set<UUID> guardianIds = guardians ? guardiansOfCourse(jdbc, tenantId, ids) : Set.of();
        return new AudienceBreakdown(students, teachers, guardianIds, Set.of());
    }

    private Set<UUID> guardiansOfGrade(JdbcTemplate jdbc, UUID tenantId, UUID[] gradeIds) {
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT gdn.user_id FROM edushift.guardians gdn
                JOIN edushift.student_guardians sg ON sg.guardian_id = gdn.id
                JOIN edushift.students s ON s.id = sg.student_id
                JOIN edushift.student_enrollments se ON se.student_id = s.id
                JOIN edushift.sections sec ON sec.id = se.section_id
                JOIN edushift.grades g ON g.id = sec.grade_id
                WHERE gdn.tenant_id = ? AND sg.tenant_id = ? AND s.tenant_id = ?
                  AND se.tenant_id = ? AND sec.tenant_id = ? AND g.tenant_id = ?
                  AND g.public_uuid = ANY(?)
                  AND gdn.deleted = false AND sg.deleted = false
                  AND se.deleted = false AND se.status = 'ACTIVE'
                  AND sec.deleted = false AND s.deleted = false
                  AND gdn.user_id IS NOT NULL
                """, UUID.class, tenantId, tenantId, tenantId, tenantId, tenantId, tenantId, gradeIds));
    }

    private Set<UUID> guardiansOfSection(JdbcTemplate jdbc, UUID tenantId, UUID[] sectionIds) {
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT gdn.user_id FROM edushift.guardians gdn
                JOIN edushift.student_guardians sg ON sg.guardian_id = gdn.id
                JOIN edushift.students s ON s.id = sg.student_id
                JOIN edushift.student_enrollments se ON se.student_id = s.id
                JOIN edushift.sections sec ON sec.id = se.section_id
                WHERE gdn.tenant_id = ? AND sg.tenant_id = ? AND s.tenant_id = ?
                  AND se.tenant_id = ? AND sec.tenant_id = ?
                  AND sec.public_uuid = ANY(?)
                  AND gdn.deleted = false AND sg.deleted = false
                  AND se.deleted = false AND se.status = 'ACTIVE'
                  AND sec.deleted = false AND s.deleted = false
                  AND gdn.user_id IS NOT NULL
                """, UUID.class, tenantId, tenantId, tenantId, tenantId, tenantId, sectionIds));
    }

    private Set<UUID> guardiansOfCourse(JdbcTemplate jdbc, UUID tenantId, UUID[] courseIds) {
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT gdn.user_id FROM edushift.guardians gdn
                JOIN edushift.student_guardians sg ON sg.guardian_id = gdn.id
                JOIN edushift.students s ON s.id = sg.student_id
                JOIN edushift.student_enrollments se ON se.student_id = s.id
                JOIN edushift.sections sec ON sec.id = se.section_id
                JOIN edushift.teacher_assignments ta ON ta.section_id = sec.id
                JOIN edushift.courses c ON c.id = ta.course_id
                WHERE gdn.tenant_id = ? AND sg.tenant_id = ? AND s.tenant_id = ?
                  AND se.tenant_id = ? AND sec.tenant_id = ? AND ta.tenant_id = ? AND c.tenant_id = ?
                  AND c.public_uuid = ANY(?)
                  AND gdn.deleted = false AND sg.deleted = false
                  AND se.deleted = false AND se.status = 'ACTIVE'
                  AND sec.deleted = false AND s.deleted = false
                  AND c.deleted = false AND ta.deleted = false AND ta.unassigned_at IS NULL
                  AND gdn.user_id IS NOT NULL
                """, UUID.class,
                tenantId, tenantId, tenantId, tenantId, tenantId, tenantId, tenantId, courseIds));
    }

    private static AudienceBreakdown bucketByRole(JdbcTemplate jdbc, String sql, Object... args) {
        Set<UUID> students = new LinkedHashSet<>();
        Set<UUID> teachers = new LinkedHashSet<>();
        Set<UUID> guardians = new LinkedHashSet<>();
        Set<UUID> others = new LinkedHashSet<>();
        jdbc.query(sql, rs -> {
            UUID id = rs.getObject(1, UUID.class);
            String role = rs.getString(2);
            if (id == null) return;
            if (role == null) {
                others.add(id);
                return;
            }
            switch (role) {
                case "STUDENT" -> students.add(id);
                case "TEACHER" -> teachers.add(id);
                case "PARENT" -> guardians.add(id);
                default -> others.add(id);
            }
        }, args);
        return new AudienceBreakdown(students, teachers, guardians, others);
    }

    private static List<UUID> parseUuids(List<String> raw) {
        List<UUID> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw) {
            try {
                out.add(UUID.fromString(s));
            } catch (Exception ignored) {
                // non-UUID tokens are dropped; callers that require a 1:1
                // match (teacher scope) compare sizes and reject the rest.
            }
        }
        return out;
    }

    public record AudienceBreakdown(
            Set<UUID> students,
            Set<UUID> teachers,
            Set<UUID> guardians,
            Set<UUID> others
    ) {
        public static AudienceBreakdown empty() {
            return new AudienceBreakdown(Set.of(), Set.of(), Set.of(), Set.of());
        }

        public List<UUID> distinct() {
            LinkedHashSet<UUID> all = new LinkedHashSet<>();
            all.addAll(students);
            all.addAll(teachers);
            all.addAll(guardians);
            all.addAll(others);
            return List.copyOf(all);
        }
    }
}
