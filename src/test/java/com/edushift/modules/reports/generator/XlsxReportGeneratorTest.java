package com.edushift.modules.reports.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.reports.entity.ReportJob.Format;
import com.edushift.modules.reports.entity.ReportJob.ReportType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;

class XlsxReportGeneratorTest {

    private final XlsxReportGenerator gen = new XlsxReportGenerator(
            new CsvReportGenerator(Mockito.mock(DataSource.class, Mockito.RETURNS_DEEP_STUBS)));

    @org.junit.jupiter.api.BeforeEach
    void setTenant() {
        com.edushift.shared.multitenancy.TenantContext.set(UUID.randomUUID());
    }

    @org.junit.jupiter.api.AfterEach
    void clearTenant() {
        com.edushift.shared.multitenancy.TenantContext.clear();
    }

    private ReportJob stubJob(ReportType type) {
        var j = new ReportJob();
        j.setId(UUID.randomUUID());
        j.setTenantId(com.edushift.shared.multitenancy.TenantContext.currentRequired());
        j.setReportType(type);
        j.setFormat(Format.XLSX);
        return j;
    }

    private Sheet parseSheet(byte[] bytes) throws IOException {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            return wb.getSheetAt(0);
        }
    }

    @Test
    void attendanceWorkbookPreservesQuotedFieldsAndWritesTextInsteadOfFormulas() throws IOException {
        CsvReportGenerator csv = Mockito.mock(CsvReportGenerator.class);
        ReportJob job = stubJob(ReportType.ATTENDANCE_SUMMARY);
        Mockito.when(csv.generate(job)).thenReturn(
                ("\uFEFFstudent,notes,empty\r\n\"Díaz, Ana\",\"He said \"\"hola\"\"\nnext line\",\r\n"
                        + "=1+1,safe,\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] bytes = new XlsxReportGenerator(csv).generate(job);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("student");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Díaz, Ana");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("He said \"hola\"\nnext line");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEmpty();
            assertThat(sheet.getRow(2).getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("=1+1");
        }
    }

    @Test
    @DisplayName("generate ATTENDANCE_SUMMARY — 8 columns incl. 'section' header")
    void attendanceSheet() throws IOException {
        var job = stubJob(ReportType.ATTENDANCE_SUMMARY);

        var bytes = gen.generate(job);
        var sheet = parseSheet(bytes);

        assertThat(sheet).isNotNull();
        var header = sheet.getRow(0);
        assertThat(header).isNotNull();
        assertThat((int) header.getLastCellNum()).isEqualTo(9);
        assertThat(header.getCell(0).getStringCellValue()).isEqualTo("session_uuid");
        assertThat(header.getCell(8).getStringCellValue()).isEqualTo("excused");
    }

    @Test
    @DisplayName("generate GRADE_BOOK — 6 columns with course/student/grade")
    void gradeBookSheet() throws IOException {
        var job = stubJob(ReportType.GRADE_BOOK);

        var bytes = gen.generate(job);
        var sheet = parseSheet(bytes);

        var header = sheet.getRow(0);
        assertThat((int) header.getLastCellNum()).isEqualTo(6);
        assertThat(header.getCell(0).getStringCellValue()).isEqualTo("course");
        assertThat(header.getCell(1).getStringCellValue()).isEqualTo("student");
        assertThat(header.getCell(2).getStringCellValue()).isEqualTo("evaluation");
        assertThat(header.getCell(3).getStringCellValue()).isEqualTo("grade");
        assertThat(header.getCell(4).getStringCellValue()).isEqualTo("max_grade");
        assertThat(header.getCell(5).getStringCellValue()).isEqualTo("recorded_at");
    }

    @Test
    @DisplayName("generate PERIOD_CLOSE — 5 columns (section, student, avg, attendance, status)")
    void periodCloseSheet() throws IOException {
        var bytes = gen.generate(stubJob(ReportType.PERIOD_CLOSE));
        var sheet = parseSheet(bytes);

        var header = sheet.getRow(0);
        assertThat((int) header.getLastCellNum()).isEqualTo(5);
        assertThat(header.getCell(0).getStringCellValue()).isEqualTo("section");
        assertThat(header.getCell(4).getStringCellValue()).isEqualTo("status");
    }

    @Test
    @DisplayName("generate STUDENT_TRANSCRIPT — 6 columns (period, course, evaluation, grade, max, comment)")
    void transcriptSheet() throws IOException {
        var bytes = gen.generate(stubJob(ReportType.STUDENT_TRANSCRIPT));
        var sheet = parseSheet(bytes);

        var header = sheet.getRow(0);
        assertThat((int) header.getLastCellNum()).isEqualTo(6);
        assertThat(header.getCell(0).getStringCellValue()).isEqualTo("period");
        assertThat(header.getCell(5).getStringCellValue()).isEqualTo("teacher_comment");
    }

    @Test
    @DisplayName("generate — every report type yields a parseable workbook with 2 rows")
    void allReportTypesParseable() throws IOException {
        for (var type : ReportType.values()) {
            var bytes = gen.generate(stubJob(type));
            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                Sheet sh = wb.getSheetAt(0);
                assertThat(sh.getSheetName()).isEqualTo(type.name());
                assertThat(sh.getPhysicalNumberOfRows()).isEqualTo(
                        type == ReportType.ATTENDANCE_SUMMARY ? 1 : 2);
            }
        }
    }
}
