package com.edushift.modules.reports.generator;

import com.edushift.modules.reports.entity.ReportJob;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * XLSX report generator (Sprint 9 / BE-9.2).
 *
 * <p>For MVP we emit a "no data" sheet with the report name + a
 * row of headers describing what the report would contain. The CSV
 * version has the real SQL; the XLSX version shares the same
 * "wrap the data" shape but is more useful for admins who like
 * formatted spreadsheets.</p>
 */
@Component
public class XlsxReportGenerator {

    public byte[] generate(ReportJob job) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024)) {
            Sheet sheet = wb.createSheet(job.getReportType().name());
            int row = 0;
            Row header = sheet.createRow(row++);
            String[] cols = columnsFor(job.getReportType());
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
            }
            // Data row (placeholder; real impl would run the SQL).
            Row data = sheet.createRow(row++);
            for (int i = 0; i < cols.length; i++) {
                data.createCell(i).setCellValue("—");
            }
            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static String[] columnsFor(ReportJob.ReportType t) {
        return switch (t) {
            case ATTENDANCE_SUMMARY -> new String[] {
                    "section", "date", "slot", "total", "present", "absent", "late", "excused"};
            case GRADE_BOOK -> new String[] {
                    "course", "student", "evaluation", "grade", "max_grade", "recorded_at"};
            case PERIOD_CLOSE -> new String[] {
                    "section", "student", "avg_grade", "attendance_pct", "status"};
            case STUDENT_TRANSCRIPT -> new String[] {
                    "period", "course", "evaluation", "grade", "max_grade", "teacher_comment"};
        };
    }
}
