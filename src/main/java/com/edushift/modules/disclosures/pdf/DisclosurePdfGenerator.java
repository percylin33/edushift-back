package com.edushift.modules.disclosures.pdf;

import com.edushift.modules.disclosures.dto.DisclosureAttachment;
import com.edushift.modules.disclosures.dto.DisclosureCatalog;
import com.edushift.modules.disclosures.dto.DisclosureContent;
import com.edushift.modules.disclosures.dto.DisclosureField;
import com.edushift.modules.disclosures.dto.DisclosureFieldState;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Renders a clean, printable school disclosure copy from the stored snapshot. */
@Component
public class DisclosurePdfGenerator {
	private static final ZoneId LIMA = ZoneId.of("America/Lima");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
			.withZone(LIMA);

	public byte[] generate(String schoolName, String yearName, Integer version, DisclosureContent content, boolean draft) {
		ByteArrayOutputStream output = new ByteArrayOutputStream(24 * 1024);
		try (Document document = new Document(PageSize.A4, 48, 48, 54, 46)) {
			PdfWriter.getInstance(document, output);
			document.open();
			Font school = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(30, 58, 95));
			Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
			Font section = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(30, 58, 95));
			Font label = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);
			Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
			Font note = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
			document.add(new Paragraph(safe(schoolName), school));
			document.add(new Paragraph("Información del servicio educativo · " + safe(yearName), title));
			document.add(new Paragraph(draft ? "COPIA DE REVISIÓN — aún no publicada" : "Versión publicada " + version,
					FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, draft ? new Color(180, 83, 9) : new Color(22, 101, 52))));
			document.add(new Paragraph("Generada el " + DATE_TIME.format(Instant.now()) + " (hora de Perú)", note));
			document.add(new Paragraph(" "));
			Map<String, DisclosureField> fields = content.fields() == null ? Map.of() : content.fields();
			Map<UUID, String> sourceLabels = content.attachments() == null ? Map.of() : content.attachments().stream()
					.collect(Collectors.toMap(DisclosureAttachment::filePublicUuid, DisclosureAttachment::label));
			String lastSection = "";
			for (var definition : DisclosureCatalog.fields()) {
				DisclosureField value = fields.get(definition.key());
				if ("school_schedule".equals(definition.key())
						&& (value == null || value.state() == null || value.state() == DisclosureFieldState.PENDING)) continue;
				if (!definition.section().equals(lastSection)) {
					lastSection = definition.section();
					document.add(new Paragraph(lastSection, section));
				}
				document.add(new Paragraph(definition.label(), label));
				if (value == null || value.state() == null || value.state() == DisclosureFieldState.PENDING) {
					document.add(new Paragraph("Pendiente de completar por la institución.", note));
				} else if (value.state() == DisclosureFieldState.NOT_APPLICABLE) {
					document.add(new Paragraph("No aplica. " + safe(value.note()), body));
				} else if (value.documentOnly()) {
					document.add(new Paragraph("Respuesta en el documento fuente indicado abajo.", body));
				} else {
					document.add(new Paragraph(safe(value.value()), body));
				}
				if (value != null && value.sources() != null && !value.sources().isEmpty()) {
					String sources = value.sources().stream().map(source -> safe(sourceLabels.get(source.filePublicUuid())
							+ (source.pageReference() == null || source.pageReference().isBlank() ? "" : ", " + source.pageReference())))
							.collect(Collectors.joining("; "));
					document.add(new Paragraph("Fuente: " + sources, note));
				}
				document.add(new Paragraph(" "));
			}
			if (content.attachments() != null && !content.attachments().isEmpty()) {
				document.add(new Paragraph("Anexos disponibles en el portal familiar", section));
				for (DisclosureAttachment attachment : content.attachments()) {
					if (Boolean.TRUE.equals(attachment.visibleToFamilies())) {
						document.add(new Paragraph("• " + safe(attachment.label()) + documentDetails(attachment), body));
					}
				}
			}
			if (content.additionalInformation() != null && !content.additionalInformation().isBlank()) {
				document.add(new Paragraph("Información adicional", section));
				document.add(new Paragraph(safe(content.additionalInformation()), body));
			}
			document.add(new Paragraph(" "));
			document.add(new Paragraph("Este documento reproduce información publicada por la institución. La descarga acredita una operación técnica de acceso y no constituye aceptación ni consentimiento.", note));
			document.close();
		}
		catch (Exception exception) {
			throw new IllegalStateException("No se pudo generar la copia imprimible del expediente", exception);
		}
		return output.toByteArray();
	}

	private static String safe(String text) { return text == null || text.isBlank() ? "—" : text.trim(); }

	private static String documentDetails(DisclosureAttachment attachment) {
		StringBuilder details = new StringBuilder();
		if (attachment.versionLabel() != null && !attachment.versionLabel().isBlank()) details.append(" · ").append(attachment.versionLabel().trim());
		if (attachment.documentDate() != null) details.append(" · emitido ").append(attachment.documentDate());
		if (attachment.validUntil() != null) details.append(" · vigente hasta ").append(attachment.validUntil());
		return details.toString();
	}
}
