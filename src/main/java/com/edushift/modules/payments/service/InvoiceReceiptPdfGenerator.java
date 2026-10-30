package com.edushift.modules.payments.service;

import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.payments.entity.InvoiceItem;
import com.edushift.shared.branding.PlatformBrandAssets;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Branded OpenPDF receipt for paid invoices (school logo + primary color).
 */
@Component
@Slf4j
public class InvoiceReceiptPdfGenerator {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");
	private static final DateTimeFormatter DATE_TIME =
			DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(LIMA);
	private static final DateTimeFormatter DATE =
			DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(LIMA);
	private static final Color FALLBACK_PRIMARY = new Color(0x1e, 0x3a, 0x5f);
	private static final Color LIGHT_ROW = new Color(0xf8, 0xfa, 0xfc);
	private static final Color BORDER = new Color(0xe2, 0xe8, 0xf0);
	private static final Color MUTED = new Color(0x64, 0x74, 0x8b);

	public record ReceiptContext(
			String schoolName,
			String primaryColorHex,
			byte[] logoBytes,
			String studentName,
			Invoice invoice,
			List<InvoiceItem> items
	) {
	}

	public byte[] generate(ReceiptContext ctx) {
		Invoice invoice = ctx.invoice();
		Color primary = parseColor(ctx.primaryColorHex());
		ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
		try (Document doc = new Document(PageSize.A4, 48, 48, 48, 48)) {
			PdfWriter.getInstance(doc, out);
			doc.open();

			Font schoolFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, primary);
			Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
			Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, MUTED);
			Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
			Font bodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);
			Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
			Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, primary);
			Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);

			addHeader(doc, ctx.schoolName(), ctx.logoBytes(), schoolFont, bodyFont, primary);
			doc.add(spacer(10));

			Paragraph title = new Paragraph("Comprobante de pago", titleFont);
			title.setAlignment(Element.ALIGN_LEFT);
			doc.add(title);

			String folio = folioOf(invoice);
			Paragraph meta = new Paragraph(
					"Folio " + folio + "  ·  Periodo " + nullToDash(invoice.getPeriodLabel()),
					bodyFont);
			meta.setSpacingBefore(4);
			meta.setSpacingAfter(12);
			doc.add(meta);

			doc.add(metaTable(invoice, ctx.studentName(), labelFont, bodyFont, bodyBold));
			doc.add(spacer(14));

			doc.add(itemsTable(invoice, ctx.items(), headerFont, bodyFont, bodyBold, primary));
			doc.add(spacer(10));

			doc.add(totalsTable(invoice, bodyFont, bodyBold, totalFont, primary));
			doc.add(spacer(12));

			PdfPTable statusRow = new PdfPTable(1);
			statusRow.setWidthPercentage(100);
			PdfPCell statusCell = new PdfPCell(new Phrase(
					"Estado: " + statusEs(invoice.getStatus()), bodyBold));
			statusCell.setBorder(Rectangle.NO_BORDER);
			statusCell.setPadding(0);
			statusRow.addCell(statusCell);
			doc.add(statusRow);

			doc.add(spacer(24));
			Paragraph footer = new Paragraph(
					"Documento informativo del colegio · EduShift  ·  Generado "
							+ DATE_TIME.format(Instant.now()),
					footerFont);
			footer.setAlignment(Element.ALIGN_CENTER);
			doc.add(footer);

			doc.close();
		}
		catch (Exception e) {
			throw new IllegalStateException("PDF generation failed: " + e.getMessage(), e);
		}
		return out.toByteArray();
	}

	/**
	 * Best-effort logo fetch (same constraints as family handout PDFs).
	 * When the tenant has no reachable logo, returns the platform isotipo.
	 */
	public byte[] loadLogoBytes(String logoUrl) {
		byte[] remote = fetchLogoBytes(logoUrl);
		return remote != null ? remote : PlatformBrandAssets.markBytes();
	}

	private byte[] fetchLogoBytes(String logoUrl) {
		if (logoUrl == null || logoUrl.isBlank()) {
			return null;
		}
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) URI.create(logoUrl.trim()).toURL().openConnection();
			conn.setConnectTimeout(2500);
			conn.setReadTimeout(2500);
			conn.setInstanceFollowRedirects(true);
			try (InputStream in = conn.getInputStream()) {
				byte[] bytes = in.readAllBytes();
				if (bytes.length == 0 || bytes.length > 2_000_000) {
					return null;
				}
				return bytes;
			}
		}
		catch (Exception ex) {
			log.debug("[receipt-pdf] logo fetch failed: {}", ex.toString());
			return null;
		}
		finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	static String formatSoles(long cents) {
		return "S/ " + String.format(Locale.US, "%.2f", cents / 100.0);
	}

	static String statusEs(Invoice.Status status) {
		if (status == null) {
			return "—";
		}
		return switch (status) {
			case PAID -> "Pagado";
			case PENDING -> "Pendiente";
			case PENDING_REVIEW -> "En revisión";
			case OVERDUE -> "Vencida";
			case CANCELLED -> "Cancelada";
			case REFUNDED -> "Reembolsada";
		};
	}

	static Color parseColor(String hex) {
		if (hex == null || hex.isBlank()) {
			return FALLBACK_PRIMARY;
		}
		String h = hex.trim();
		if (h.startsWith("#")) {
			h = h.substring(1);
		}
		try {
			if (h.length() == 3) {
				int r = Integer.parseInt(h.substring(0, 1) + h.substring(0, 1), 16);
				int g = Integer.parseInt(h.substring(1, 2) + h.substring(1, 2), 16);
				int b = Integer.parseInt(h.substring(2, 3) + h.substring(2, 3), 16);
				return new Color(r, g, b);
			}
			if (h.length() == 6) {
				return new Color(Integer.parseInt(h, 16));
			}
		}
		catch (NumberFormatException ignored) {
			// fall through
		}
		return FALLBACK_PRIMARY;
	}

	private static void addHeader(
			Document doc,
			String schoolName,
			byte[] logoBytes,
			Font schoolFont,
			Font bodyFont,
			Color primary
	) throws Exception {
		PdfPTable header = new PdfPTable(new float[]{1.2f, 4f});
		header.setWidthPercentage(100);

		PdfPCell logoCell = new PdfPCell();
		logoCell.setBorder(Rectangle.NO_BORDER);
		logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		if (logoBytes != null && logoBytes.length > 0) {
			Image logo = Image.getInstance(logoBytes);
			logo.scaleToFit(72, 72);
			logoCell.addElement(logo);
		}
		else {
			logoCell.addElement(new Paragraph(" ", bodyFont));
		}
		header.addCell(logoCell);

		PdfPCell textCell = new PdfPCell();
		textCell.setBorder(Rectangle.NO_BORDER);
		textCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		textCell.addElement(new Paragraph(nullToDash(schoolName), schoolFont));
		textCell.addElement(new Paragraph("Comprobante oficial de pagos", bodyFont));
		header.addCell(textCell);
		doc.add(header);

		PdfPTable bar = new PdfPTable(1);
		bar.setWidthPercentage(100);
		bar.setSpacingBefore(8);
		PdfPCell barCell = new PdfPCell();
		barCell.setFixedHeight(4f);
		barCell.setBackgroundColor(primary);
		barCell.setBorder(Rectangle.NO_BORDER);
		bar.addCell(barCell);
		doc.add(bar);
	}

	private static PdfPTable metaTable(
			Invoice invoice,
			String studentName,
			Font labelFont,
			Font bodyFont,
			Font bodyBold
	) throws Exception {
		PdfPTable table = new PdfPTable(new float[]{1.2f, 3.8f});
		table.setWidthPercentage(100);
		addMetaRow(table, "Alumno", nullToDash(studentName), labelFont, bodyBold);
		addMetaRow(table, "Periodo", nullToDash(invoice.getPeriodLabel()), labelFont, bodyFont);
		addMetaRow(table, "Emitida", formatDate(invoice.getIssuedAt()), labelFont, bodyFont);
		addMetaRow(table, "Pagada", formatDateTime(invoice.getPaidAt()), labelFont, bodyFont);
		return table;
	}

	private static void addMetaRow(
			PdfPTable table,
			String label,
			String value,
			Font labelFont,
			Font valueFont
	) {
		PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
		l.setBorderColor(BORDER);
		l.setPadding(6);
		l.setBackgroundColor(LIGHT_ROW);
		table.addCell(l);
		PdfPCell v = new PdfPCell(new Phrase(value, valueFont));
		v.setBorderColor(BORDER);
		v.setPadding(6);
		table.addCell(v);
	}

	private static PdfPTable itemsTable(
			Invoice invoice,
			List<InvoiceItem> items,
			Font headerFont,
			Font bodyFont,
			Font bodyBold,
			Color primary
	) throws Exception {
		PdfPTable table = new PdfPTable(new float[]{4.2f, 1f, 1.4f, 1.4f});
		table.setWidthPercentage(100);
		addHeaderCell(table, "Descripción", headerFont, primary, Element.ALIGN_LEFT);
		addHeaderCell(table, "Cant.", headerFont, primary, Element.ALIGN_RIGHT);
		addHeaderCell(table, "Unitario", headerFont, primary, Element.ALIGN_RIGHT);
		addHeaderCell(table, "Subtotal", headerFont, primary, Element.ALIGN_RIGHT);

		if (items == null || items.isEmpty()) {
			addItemRow(
					table,
					"Cuota " + nullToDash(invoice.getPeriodLabel()),
					1,
					invoice.getTotalCents(),
					invoice.getTotalCents(),
					bodyFont,
					bodyBold);
		}
		else {
			for (InvoiceItem it : items) {
				addItemRow(
						table,
						nullToDash(it.getDescription()),
						it.getQuantity(),
						it.getUnitAmountCents(),
						it.getLineTotalCents(),
						bodyFont,
						bodyBold);
			}
		}
		return table;
	}

	private static void addHeaderCell(
			PdfPTable table,
			String text,
			Font font,
			Color bg,
			int align
	) {
		PdfPCell cell = new PdfPCell(new Phrase(text, font));
		cell.setBackgroundColor(bg);
		cell.setBorderColor(bg);
		cell.setPadding(7);
		cell.setHorizontalAlignment(align);
		table.addCell(cell);
	}

	private static void addItemRow(
			PdfPTable table,
			String description,
			int quantity,
			long unitCents,
			long lineCents,
			Font bodyFont,
			Font bodyBold
	) {
		PdfPCell d = new PdfPCell(new Phrase(description, bodyFont));
		d.setBorderColor(BORDER);
		d.setPadding(6);
		table.addCell(d);

		PdfPCell q = new PdfPCell(new Phrase(String.valueOf(quantity), bodyFont));
		q.setBorderColor(BORDER);
		q.setPadding(6);
		q.setHorizontalAlignment(Element.ALIGN_RIGHT);
		table.addCell(q);

		PdfPCell u = new PdfPCell(new Phrase(formatSoles(unitCents), bodyFont));
		u.setBorderColor(BORDER);
		u.setPadding(6);
		u.setHorizontalAlignment(Element.ALIGN_RIGHT);
		table.addCell(u);

		PdfPCell s = new PdfPCell(new Phrase(formatSoles(lineCents), bodyBold));
		s.setBorderColor(BORDER);
		s.setPadding(6);
		s.setHorizontalAlignment(Element.ALIGN_RIGHT);
		table.addCell(s);
	}

	private static PdfPTable totalsTable(
			Invoice invoice,
			Font bodyFont,
			Font bodyBold,
			Font totalFont,
			Color primary
	) throws Exception {
		PdfPTable wrap = new PdfPTable(1);
		wrap.setWidthPercentage(45);
		wrap.setHorizontalAlignment(Element.ALIGN_RIGHT);

		PdfPTable inner = new PdfPTable(new float[]{1.6f, 1.4f});
		inner.setWidthPercentage(100);
		addTotalRow(inner, "Subtotal", formatSoles(invoice.getSubtotalCents()), bodyFont, bodyFont);
		if (invoice.getDiscountCents() > 0) {
			addTotalRow(inner, "Descuento", "-" + formatSoles(invoice.getDiscountCents()), bodyFont, bodyFont);
		}
		if (invoice.getMoraCents() > 0) {
			addTotalRow(inner, "Mora", formatSoles(invoice.getMoraCents()), bodyFont, bodyFont);
		}
		PdfPCell totalLabel = new PdfPCell(new Phrase("Total", totalFont));
		totalLabel.setBorder(Rectangle.TOP);
		totalLabel.setBorderColor(primary);
		totalLabel.setBorderWidthTop(1.5f);
		totalLabel.setPaddingTop(8);
		totalLabel.setPaddingBottom(4);
		inner.addCell(totalLabel);
		PdfPCell totalValue = new PdfPCell(new Phrase(formatSoles(invoice.getTotalCents()), totalFont));
		totalValue.setBorder(Rectangle.TOP);
		totalValue.setBorderColor(primary);
		totalValue.setBorderWidthTop(1.5f);
		totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
		totalValue.setPaddingTop(8);
		totalValue.setPaddingBottom(4);
		inner.addCell(totalValue);

		PdfPCell holder = new PdfPCell(inner);
		holder.setBorder(Rectangle.NO_BORDER);
		holder.setPadding(0);
		wrap.addCell(holder);
		return wrap;
	}

	private static void addTotalRow(
			PdfPTable table,
			String label,
			String value,
			Font labelFont,
			Font valueFont
	) {
		PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
		l.setBorder(Rectangle.NO_BORDER);
		l.setPadding(3);
		table.addCell(l);
		PdfPCell v = new PdfPCell(new Phrase(value, valueFont));
		v.setBorder(Rectangle.NO_BORDER);
		v.setHorizontalAlignment(Element.ALIGN_RIGHT);
		v.setPadding(3);
		table.addCell(v);
	}

	private static Paragraph spacer(float points) {
		Paragraph p = new Paragraph(" ");
		p.setSpacingBefore(0);
		p.setSpacingAfter(points);
		return p;
	}

	private static String folioOf(Invoice invoice) {
		if (invoice.getPublicUuid() == null) {
			return "—";
		}
		String s = invoice.getPublicUuid().toString().replace("-", "");
		return s.substring(0, Math.min(8, s.length())).toUpperCase(Locale.ROOT);
	}

	private static String formatDate(Instant instant) {
		return instant == null ? "—" : DATE.format(instant);
	}

	private static String formatDateTime(Instant instant) {
		return instant == null ? "—" : DATE_TIME.format(instant);
	}

	private static String nullToDash(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}
}
