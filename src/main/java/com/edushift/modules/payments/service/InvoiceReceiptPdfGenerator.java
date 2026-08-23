package com.edushift.modules.payments.service;

import com.edushift.modules.payments.entity.Invoice;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class InvoiceReceiptPdfGenerator {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Lima"));

    public byte[] generate(
            String schoolName,
            String studentName,
            Invoice invoice) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 * 1024);
        try (Document doc = new Document(PageSize.A4, 48, 48, 56, 56)) {
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Paragraph heading = new Paragraph("Comprobante de pago", title);
            heading.setAlignment(Element.ALIGN_LEFT);
            doc.add(heading);
            doc.add(new Paragraph(" ", body));
            doc.add(new Paragraph("Colegio: " + nullToDash(schoolName), body));
            doc.add(new Paragraph("Alumno: " + nullToDash(studentName), body));
            doc.add(new Paragraph("Periodo: " + nullToDash(invoice.getPeriodLabel()), body));
            doc.add(new Paragraph(
                    "Monto: " + nullToDash(invoice.getCurrency()) + " "
                            + String.format("%.2f", invoice.getTotalCents() / 100.0),
                    body));
            doc.add(new Paragraph(
                    "Estado: " + (invoice.getStatus() == null ? "—" : invoice.getStatus().name()),
                    body));
            doc.add(new Paragraph(
                    "Fecha de pago: "
                            + (invoice.getPaidAt() == null ? "—" : DATE.format(invoice.getPaidAt())),
                    body));
            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("PDF generation failed: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
