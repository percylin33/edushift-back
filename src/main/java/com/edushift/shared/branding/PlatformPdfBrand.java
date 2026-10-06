package com.edushift.shared.branding;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Shared OpenPDF header and footer for institutional documents.
 * Header uses the school logo when bytes are available, otherwise the
 * platform isotipo. The footer is the platform seal plus the EduShift name.
 */
public final class PlatformPdfBrand {

	private static final Color FOOTER = new Color(100, 116, 139);
	private static final float FOOTER_MARGIN = 52f;

	private PlatformPdfBrand() {
	}

	/**
	 * Call before {@link Document#open()} so the seal sits in the bottom margin
	 * of every page.
	 */
	public static void installFooter(Document doc, PdfWriter writer) {
		if (doc == null || writer == null) {
			return;
		}
		float bottom = Math.max(doc.bottomMargin(), FOOTER_MARGIN);
		doc.setMargins(doc.leftMargin(), doc.rightMargin(), doc.topMargin(), bottom);
		writer.setPageEvent(new SealFooter());
	}

	/**
	 * Header row: school logo, or the platform isotipo when {@code schoolLogo} is empty.
	 */
	public static void writeHeader(Document doc, String title, byte[] schoolLogo) throws DocumentException {
		byte[] logo = schoolLogo != null && schoolLogo.length > 0
				? schoolLogo
				: PlatformBrandAssets.markBytes();
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setWidths(new float[] { 14f, 86f });
		table.setSpacingAfter(12f);

		PdfPCell logoCell = new PdfPCell();
		logoCell.setBorder(Rectangle.NO_BORDER);
		logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		Image image = imageOrNull(logo, 48, 48);
		if (image != null) {
			logoCell.addElement(image);
		}
		table.addCell(logoCell);

		PdfPCell titleCell = new PdfPCell(new Phrase(
				title == null ? "" : title,
				FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
		titleCell.setBorder(Rectangle.NO_BORDER);
		titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		titleCell.setPaddingLeft(8f);
		table.addCell(titleCell);
		doc.add(table);
	}

	/** Best-effort HTTP fetch used when a tenant published a logo URL. */
	public static byte[] fetchLogo(String logoUrl) {
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
			return null;
		}
		finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	private static Image imageOrNull(byte[] bytes, float maxW, float maxH) {
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		try {
			Image image = Image.getInstance(bytes);
			image.scaleToFit(maxW, maxH);
			return image;
		}
		catch (Exception ex) {
			return null;
		}
	}

	private static final class SealFooter extends PdfPageEventHelper {
		private final byte[] seal = PlatformBrandAssets.read(PlatformBrandAssets.SEAL);

		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			PdfContentByte cb = writer.getDirectContent();
			float y = document.bottom() - 28f;
			Image image = imageOrNull(seal, 22, 22);
			if (image != null) {
				try {
					image.setAbsolutePosition(document.leftMargin(), y);
					cb.addImage(image);
				}
				catch (DocumentException ex) {
					// Footer art is decorative; the document still ships.
				}
			}
			ColumnText.showTextAligned(
					cb,
					Element.ALIGN_RIGHT,
					new Phrase("EduShift", FontFactory.getFont(FontFactory.HELVETICA, 8, FOOTER)),
					document.right(),
					y + 6f,
					0);
		}
	}
}
