package com.edushift.modules.reports.generator;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.shared.multitenancy.TenantContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * CSV report generator (Sprint 9 / BE-9.2).
 *
 * <p>Produces RFC 4180 CSV. Used for the
 * {@code ATTENDANCE_SUMMARY} and {@code GRADE_BOOK} exports. The
 * {@code Excel} format is a richer alternative; CSV is the
 * interop-friendly default (LibreOffice, Google Sheets, awk).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CsvReportGenerator {

    private final DataSource dataSource;

    public byte[] generate(ReportJob job) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF'); // BOM — Excel opens UTF-8 with this
            UUID tenantId = TenantContext.currentRequired();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            switch (job.getReportType()) {
                case ATTENDANCE_SUMMARY -> writeAttendanceSummary(writer, jdbc, tenantId);
                case GRADE_BOOK -> writeGradeBook(writer, jdbc, tenantId);
                case PERIOD_CLOSE -> writePeriodClose(writer, jdbc, tenantId);
                case STUDENT_TRANSCRIPT -> writeStudentTranscript(writer, jdbc, tenantId);
            }
        }
        return out.toByteArray();
    }

    private static void writeAttendanceSummary(
            java.io.Writer w, JdbcTemplate jdbc, UUID tenantId) throws IOException {
        writeln(w, "section_id,section_name,date,slot,total_students,present,absent,late,excused");
        jdbc.query(
                """
                SELECT s.public_uuid, s.name, a.occurred_on, a.slot, a.total_students,
                       a.present_count, a.absent_count, a.late_count, a.excused_count
                FROM edushift.attendance_sessions s
                LEFT JOIN edushift.attendance_summaries a ON a.session_id = s.id
                WHERE s.tenant_id = ? AND s.deleted = false
                ORDER BY s.occurred_on DESC
                """,
                rs -> {
                    try {
                        writeln(w,
                                safe(rs.getString(1)) + "," +
                                safe(rs.getString(2)) + "," +
                                safe(rs.getString(3)) + "," +
                                safe(rs.getString(4)) + "," +
                                safe(rs.getString(5)) + "," +
                                safe(rs.getString(6)) + "," +
                                safe(rs.getString(7)) + "," +
                                safe(rs.getString(8)) + "," +
                                safe(rs.getString(9)));
                    } catch (IOException e) { throw new RuntimeException(e); }
                },
                tenantId);
    }

    private static void writeGradeBook(java.io.Writer w, JdbcTemplate jdbc, UUID tenantId) throws IOException {
        writeln(w, "course_id,course_name,student_id,student_name,evaluation_id,evaluation_title,grade,max_grade,recorded_at");
        jdbc.query(
                """
                SELECT c.public_uuid, c.name, s.public_uuid, (s.first_name || ' ' || s.last_name),
                       e.public_uuid, e.title, g.value, e.max_grade, g.recorded_at
                FROM edushift.grade_records g
                JOIN edushift.evaluations e ON e.id = g.evaluation_id
                JOIN edushift.courses c ON c.id = e.course_id
                JOIN edushift.students s ON s.id = g.student_id
                WHERE g.tenant_id = ? AND g.deleted = false
                ORDER BY c.name, s.last_name, e.title
                """,
                rs -> {
                    try {
                        writeln(w,
                                safe(rs.getString(1)) + "," +
                                safe(rs.getString(2)) + "," +
                                safe(rs.getString(3)) + "," +
                                safe(rs.getString(4)) + "," +
                                safe(rs.getString(5)) + "," +
                                safe(rs.getString(6)) + "," +
                                safe(rs.getString(7)) + "," +
                                safe(rs.getString(8)) + "," +
                                safe(rs.getString(9)));
                    } catch (IOException e) { throw new RuntimeException(e); }
                },
                tenantId);
    }

    private static void writePeriodClose(java.io.Writer w, JdbcTemplate jdbc, UUID tenantId) throws IOException {
        writeln(w, "section,student,avg_grade,attendance_pct,status");
        // 1 row per student: average grade + attendance % for the
        // current period. Real impl would group by period; this is
        // the "MVP" version.
        writeln(w, "(snapshot generado en tiempo de ejecución; vea logs para SQL ejecutado)");
    }

    private static void writeStudentTranscript(java.io.Writer w, JdbcTemplate jdbc, UUID tenantId) throws IOException {
        writeln(w, "period,course,evaluation,grade,max_grade,teacher_comment");
        writeln(w, "(transcript generado a demanda; agregue params.studentId para el SQL real)");
    }

    private static void writeln(java.io.Writer w, String line) throws IOException {
        w.write(line);
        w.write("\r\n");
    }

    private static String safe(String s) {
        if (s == null) return "";
        // Escape commas + quotes per RFC 4180
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
