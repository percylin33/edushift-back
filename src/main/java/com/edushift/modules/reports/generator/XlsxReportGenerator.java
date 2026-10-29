package com.edushift.modules.reports.generator;

import com.edushift.modules.reports.entity.ReportJob;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * XLSX report generator (Sprint 9 / BE-9.2).
 *
 * <p>Attendance exports reuse the tenant-scoped CSV query and convert its
 * rows to a real XLSX workbook.</p>
 */
@Component
@RequiredArgsConstructor
public class XlsxReportGenerator {

    private final CsvReportGenerator csvReportGenerator;

    public byte[] generate(ReportJob job) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024)) {
            Sheet sheet = wb.createSheet(job.getReportType().name());
            if (job.getReportType() == ReportJob.ReportType.ATTENDANCE_SUMMARY) {
                writeCsvRows(sheet, csvReportGenerator.generate(job));
                wb.write(out);
                return out.toByteArray();
            }
            int rowIdx = 0;
            Row header = sheet.createRow(rowIdx++);
            String[] cols = columnsFor(job.getReportType());
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
            }
            Row data = sheet.createRow(rowIdx++);
            for (int i = 0; i < cols.length; i++) {
                data.createCell(i).setCellValue("—");
            }
            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void writeCsvRows(Sheet sheet, byte[] csv) throws IOException {
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        int start = text.startsWith("\uFEFF") ? 1 : 0;
        boolean quoted = false;
        StringBuilder value = new StringBuilder();
        Row row = sheet.createRow(0);
        int column = 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && (ch == ',' || ch == '\r' || ch == '\n')) {
                row.createCell(column++).setCellValue(value.toString());
                value.setLength(0);
                if (ch != ',') {
                    if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                    if (i + 1 < text.length()) row = sheet.createRow(row.getRowNum() + 1);
                    column = 0;
                }
            } else {
                value.append(ch);
            }
        }
        if (quoted) throw new IOException("Unterminated quoted CSV field in attendance export");
        if (value.length() > 0 || column > 0) row.createCell(column).setCellValue(value.toString());
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
