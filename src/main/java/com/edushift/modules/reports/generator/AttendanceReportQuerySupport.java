package com.edushift.modules.reports.generator;

import com.edushift.modules.reports.dto.AttendanceReportParams;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds parameterized SQL for attendance summary exports.
 */
final class AttendanceReportQuerySupport {

    private AttendanceReportQuerySupport() {}

    static QuerySpec sessionSummaryQuery(UUID tenantId, AttendanceReportParams params) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.public_uuid, sec.name, s.occurred_on, s.slot,
                       count(r.id) as total,
                       count(*) FILTER (WHERE r.status = 'PRESENT') as present,
                       count(*) FILTER (WHERE r.status = 'ABSENT') as absent,
                       count(*) FILTER (WHERE r.status = 'LATE') as late,
                       count(*) FILTER (WHERE r.status = 'EXCUSED') as excused
                FROM edushift.attendance_sessions s
                JOIN edushift.sections sec ON sec.id = s.section_id
                LEFT JOIN edushift.attendance_records r ON r.session_id = s.id
                WHERE s.tenant_id = ? AND s.deleted = false
                """);
        List<Object> binds = new ArrayList<>();
        binds.add(tenantId);
        appendSessionFilters(sql, binds, params);
        sql.append("""
                GROUP BY s.id, s.public_uuid, sec.name, s.occurred_on, s.slot
                ORDER BY s.occurred_on DESC, s.slot ASC
                """);
        return new QuerySpec(sql.toString(), binds);
    }

    static QuerySpec studentHistoryQuery(UUID tenantId, AttendanceReportParams params) {
        StringBuilder sql = new StringBuilder("""
                SELECT st.public_uuid as student_uuid,
                       (st.first_name || ' ' || st.last_name) as student_name,
                       sec.name as section_name,
                       s.occurred_on,
                       s.slot,
                       r.status,
                       r.occurred_at,
                       coalesce(r.notes, '') as notes
                FROM edushift.attendance_records r
                JOIN edushift.students st ON st.id = r.student_id
                JOIN edushift.attendance_sessions s ON s.id = r.session_id
                JOIN edushift.sections sec ON sec.id = s.section_id
                WHERE r.tenant_id = ? AND r.deleted = false AND s.deleted = false
                """);
        List<Object> binds = new ArrayList<>();
        binds.add(tenantId);
        if (params.studentPublicUuid() != null) {
            sql.append(" AND st.public_uuid = ? ");
            binds.add(params.studentPublicUuid());
        }
        if (params.from() != null) {
            sql.append(" AND s.occurred_on >= ? ");
            binds.add(params.from());
        }
        if (params.to() != null) {
            sql.append(" AND s.occurred_on <= ? ");
            binds.add(params.to());
        }
        if (params.sectionPublicUuid() != null) {
            sql.append(" AND sec.public_uuid = ? ");
            binds.add(params.sectionPublicUuid());
        }
        sql.append(" ORDER BY s.occurred_on DESC, r.occurred_at DESC ");
        return new QuerySpec(sql.toString(), binds);
    }

    private static void appendSessionFilters(StringBuilder sql, List<Object> binds, AttendanceReportParams params) {
        if (params.sessionPublicUuid() != null) {
            sql.append(" AND s.public_uuid = ? ");
            binds.add(params.sessionPublicUuid());
        }
        if (params.sectionPublicUuid() != null) {
            sql.append(" AND sec.public_uuid = ? ");
            binds.add(params.sectionPublicUuid());
        }
        if (params.from() != null) {
            sql.append(" AND s.occurred_on >= ? ");
            binds.add(params.from());
        }
        if (params.to() != null) {
            sql.append(" AND s.occurred_on <= ? ");
            binds.add(params.to());
        }
    }

    static void bind(PreparedStatement ps, List<Object> binds) throws SQLException {
        for (int i = 0; i < binds.size(); i++) {
            ps.setObject(i + 1, binds.get(i));
        }
    }

    record QuerySpec(String sql, List<Object> binds) {}
}
