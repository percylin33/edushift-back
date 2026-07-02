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

class XlsxReportGeneratorTest {

    private final XlsxReportGenerator gen = new XlsxReportGenerator();

    private ReportJob stubJob(ReportType type) {
        var j = new ReportJob();
        j.setId(UUID.randomUUID());
        j.setTenantId(UUID.randomUUID());
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
    @DisplayName("generate ATTENDANCE_SUMMARY — 8 columns incl. 'section' header")
    void attendanceSheet() throws IOException {
        var job = stubJob(ReportType.ATTENDANCE_SUMMARY);

        var bytes = gen.generate(job);
        var sheet = parseSheet(bytes);

        assertThat(sheet).isNotNull();
        var header = sheet.getRow(0);
        assertThat(header).isNotNull();
        assertThat((int) header.getLastCellNum()).isEqualTo(8);
        assertThat(header.getCell(0).getStringCellValue()).isEqualTo("section");
        assertThat(header.getCell(7).getStringCellValue()).isEqualTo("excused");
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
                assertThat(sh.getPhysicalNumberOfRows()).isEqualTo(2);
            }
        }
    }
}
