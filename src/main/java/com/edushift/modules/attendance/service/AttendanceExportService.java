package com.edushift.modules.attendance.service;

import com.edushift.modules.attendance.entity.AttendanceRecord;
import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.reports.entity.ReportJob;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.tenants.branding.CurrentTenantLogoLoader;
import com.edushift.shared.branding.PlatformPdfBrand;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Generates CSV/PDF/XLSX bytes for scoped student attendance exports (me / family).
 */
@Service
@RequiredArgsConstructor
public class AttendanceExportService {

    private final CurrentTenantLogoLoader tenantLogoLoader;

    private static final DateTimeFormatter DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final String[] HEADERS = {"fecha", "estado", "notas"};

    public byte[] exportRecords(
            List<AttendanceRecord> records,
            ReportJob.Format format,
            String title) throws IOException {
        return switch (format) {
            case CSV -> toCsv(records);
            case PDF -> toPdf(records, title);
            case XLSX -> toXlsx(records);
        };
    }

    private byte[] toCsv(List<AttendanceRecord> records) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            w.write('\uFEFF');
            w.write("fecha,estado,notas\r\n");
            for (AttendanceRecord r : records) {
                w.write(String.format("%s,%s,%s\r\n",
                        escape(DT.format(r.getOccurredAt())),
                        escape(label(r.getStatus())),
                        escape(r.getNotes())));
            }
        }
        return out.toByteArray();
    }

    private byte[] toXlsx(List<AttendanceRecord> records) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream(8 * 1024)) {
            Sheet sheet = wb.createSheet("Asistencia");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int rowIdx = 1;
            for (AttendanceRecord r : records) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(DT.format(r.getOccurredAt()));
                row.createCell(1).setCellValue(label(r.getStatus()));
                row.createCell(2).setCellValue(r.getNotes() == null ? "" : r.getNotes());
            }
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] toPdf(List<AttendanceRecord> records, String title) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Document doc = new Document(PageSize.A4)) {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            PlatformPdfBrand.installFooter(doc, writer);
            doc.open();
            PlatformPdfBrand.writeHeader(doc, title, tenantLogoLoader.schoolLogoOrNull());
            doc.add(new Paragraph("Generado: " + DT.format(Instant.now()),
                    FontFactory.getFont(FontFactory.HELVETICA, 10)));
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.addCell(header("Fecha"));
            table.addCell(header("Estado"));
            table.addCell(header("Notas"));
            for (AttendanceRecord r : records) {
                table.addCell(cell(DT.format(r.getOccurredAt())));
                table.addCell(cell(label(r.getStatus())));
                table.addCell(cell(r.getNotes() == null ? "" : r.getNotes()));
            }
            doc.add(table);
            doc.close();
        } catch (Exception e) {
            throw new RuntimeException("PDF export failed", e);
        }
        return out.toByteArray();
    }

    private static String label(AttendanceRecordStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PRESENT -> "Presente";
            case LATE -> "Tardanza";
            case ABSENT -> "Ausente";
            case EXCUSED -> "Justificado";
        };
    }

    private static String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static PdfPCell header(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        c.setGrayFill(0.9f);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        return c;
    }

    private static PdfPCell cell(String text) {
        return new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 9)));
    }

    public String filenameFor(Student student, ReportJob.Format format) {
        return "asistencia-" + student.getPublicUuid().toString().substring(0, 8)
                + "." + format.name().toLowerCase();
    }
}
