package com.edushift.modules.reports.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportJob.Format;
import com.edushift.modules.reports.entity.ReportJob.ReportType;
import com.edushift.shared.multitenancy.TenantContext;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CsvReportGeneratorTest {

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private PreparedStatement ps;
    @Mock private ResultSet rs;
    private CsvReportGenerator gen;

    @BeforeEach
    void setUp() throws Exception {
        gen = new CsvReportGenerator(dataSource);
        TenantContext.set(UUID.randomUUID());
        // PERIOD_CLOSE / STUDENT_TRANSCRIPT don't query the DB, so make
        // these stubbings lenient to avoid UnnecessaryStubbingException.
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(ps);
        lenient().when(ps.executeQuery()).thenReturn(rs);
        lenient().when(rs.next()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ReportJob stubJob(ReportType type) {
        var j = new ReportJob();
        j.setId(UUID.randomUUID());
        j.setTenantId(TenantContext.currentRequired());
        j.setReportType(type);
        j.setFormat(Format.CSV);
        return j;
    }

    @Test
    @DisplayName("generate ATTENDANCE_SUMMARY — header row emitted + UTF-8 BOM prefix")
    void attendance() throws IOException {
        var job = stubJob(ReportType.ATTENDANCE_SUMMARY);

        var bytes = gen.generate(job);

        assertThat(bytes).isNotEmpty();
        // UTF-8 BOM (EF BB BF) at the start
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        var body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("section_id,section_name,date,slot,total_students,"
            + "present,absent,late,excused");
    }

    @Test
    @DisplayName("generate GRADE_BOOK — header row emitted")
    void gradeBook() throws IOException {
        var job = stubJob(ReportType.GRADE_BOOK);

        var bytes = gen.generate(job);

        var body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("course_id,course_name,student_id,student_name,"
            + "evaluation_id,evaluation_title,grade,max_grade,recorded_at");
    }

    @Test
    @DisplayName("generate PERIOD_CLOSE — static placeholder rows (MVP, no SQL)")
    void periodClose() throws IOException {
        var job = stubJob(ReportType.PERIOD_CLOSE);

        var bytes = gen.generate(job);

        var body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("section,student,avg_grade,attendance_pct,status");
        assertThat(body).contains("snapshot generado en tiempo de ejecución");
    }

    @Test
    @DisplayName("generate STUDENT_TRANSCRIPT — static placeholder rows (MVP, no SQL)")
    void studentTranscript() throws IOException {
        var job = stubJob(ReportType.STUDENT_TRANSCRIPT);

        var bytes = gen.generate(job);

        var body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("period,course,evaluation,grade,max_grade,teacher_comment");
        assertThat(body).contains("params.studentId");
    }

    @Test
    @DisplayName("all report types produce non-empty bytes with CRLF line endings")
    void crlfLineEndings() throws IOException {
        for (var type : ReportType.values()) {
            var bytes = gen.generate(stubJob(type));
            assertThat(bytes).isNotEmpty();
            assertThat(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                .contains("\r\n");
        }
    }

    @Test
    @DisplayName("missing tenant context — BusinessException propagates")
    void noTenant() {
        var savedTenant = UUID.randomUUID();
        var j = new ReportJob();
        j.setId(UUID.randomUUID());
        j.setTenantId(savedTenant);
        j.setReportType(ReportType.GRADE_BOOK);
        j.setFormat(Format.CSV);
        TenantContext.clear();

        assertThatThrownBy(() -> gen.generate(j))
            .isInstanceOf(com.edushift.shared.exception.BusinessException.class);
    }
}
