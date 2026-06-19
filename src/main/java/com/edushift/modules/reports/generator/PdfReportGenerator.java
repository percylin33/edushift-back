package com.edushift.modules.reports.generator;

import com.edushift.modules.reports.entity.ReportJob;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * PDF report generator (Sprint 9 / BE-9.2).
 *
 * <p>For MVP we emit a styled "report card" with the tenant name,
 * report type, generation date, and a placeholder table. The full
 * data-fill is delegated to the CSV/XLSX generators — a separate
 * PR will switch the PDF to render the same rows.</p>
 */
@Component
public class PdfReportGenerator {

    public byte[] generate(ReportJob job) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        try (Document doc = new Document(PageSize.A4)) {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font metaFont  = FontFactory.getFont(FontFactory.HELVETICA, 10);

            Paragraph title = new Paragraph("EduShift — " + job.getReportType().name(), titleFont);
            title.setAlignment(Element.ALIGN_LEFT);
            doc.add(title);
            doc.add(new Paragraph("Generado: " + Instant.now(), metaFont));
            doc.add(new Paragraph(" "));

            // Placeholder table.
            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.addCell(headerCell("Columna A"));
            table.addCell(headerCell("Columna B"));
            table.addCell(headerCell("Columna C"));
            table.addCell(cell("Dato 1"));
            table.addCell(cell("Dato 2"));
            table.addCell(cell("Dato 3"));
            table.addCell(cell("(ver exportacion CSV/XLSX para datos completos)"));
            table.addCell(cell(""));
            table.addCell(cell(""));
            doc.add(table);

            doc.close();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    private static PdfPCell headerCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
        c.setGrayFill(0.9f);
        c.setPadding(4);
        return c;
    }

    private static PdfPCell cell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 10)));
        c.setPadding(4);
        return c;
    }
}
