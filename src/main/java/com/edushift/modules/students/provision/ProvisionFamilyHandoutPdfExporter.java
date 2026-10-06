package com.edushift.modules.students.provision;

import com.edushift.shared.branding.PlatformBrandAssets;
import com.edushift.shared.branding.PlatformPdfBrand;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CRED-7 family handout: ZIP with folder {@code Accesos - {grado/sección}/}
 * and one PDF per student named after the student.
 *
 * <p>The sheet is the printable access card (school logo, login URL, temporary
 * password and a blank box for the new password). The URL is printed in full;
 * there is no QR code.</p>
 */
@Slf4j
@Component
public class ProvisionFamilyHandoutPdfExporter {

	private static final Color PAGE = new Color(0xF1, 0xF5, 0xF9);
	private static final Color BLUE = new Color(0x25, 0x63, 0xEB);
	private static final Color INK = new Color(0x0F, 0x17, 0x2A);
	private static final Color SLATE = new Color(0x64, 0x74, 0x8B);
	private static final Color LINE = new Color(0xE2, 0xE8, 0xF0);
	private static final Color SOFT = new Color(0xF8, 0xFA, 0xFC);
	private static final Color LOGO_BADGE = new Color(0xEF, 0xF6, 0xFF);
	private static final Color DARK = new Color(0x0F, 0x17, 0x2A);
	private static final Color DARK_INSET = new Color(0x1E, 0x29, 0x3B);
	private static final Color CYAN = new Color(0x7D, 0xD3, 0xFC);
	private static final Color MIST = new Color(0xCB, 0xD5, 0xE1);
	private static final Color GREEN = new Color(0x15, 0x80, 0x3D);
	private static final Color GREEN_BG = new Color(0xF0, 0xFD, 0xF4);
	private static final Color GREEN_LINE = new Color(0xBB, 0xF7, 0xD0);
	private static final Color AMBER = new Color(0xB4, 0x53, 0x09);
	private static final Color AMBER_BG = new Color(0xFF, 0xFB, 0xEB);
	private static final Color AMBER_LINE = new Color(0xFD, 0xE6, 0x8A);
	private static final Color PILL_BG = new Color(0xEF, 0xF6, 0xFF);
	private static final Color PILL_LINE = new Color(0xBF, 0xDB, 0xFE);
	private static final Color PILL_INK = new Color(0x1E, 0x40, 0xAF);
	private static final Color PLACEHOLDER = new Color(0x94, 0xA3, 0xB8);

	/**
	 * Builds a ZIP: {@code Accesos - {section}/Nombre Alumno.pdf} per family.
	 */
	public byte[] toZip(
			String schoolName,
			String logoUrl,
			String sectionName,
			String academicYear,
			String levelName,
			String loginUrl,
			CredentialHandoutSettings handout,
			List<ProvisionCredentialRow> rows
	) {
		Map<UUID, FamilyBlock> families = groupFamilies(rows);
		String folderName = sanitizePathSegment(folderLabel(sectionName));
		byte[] logoBytes = tryLoadLogoBytes(logoUrl);
		CredentialHandoutSettings copy = handout == null
				? CredentialHandoutSettings.defaults()
				: handout;

		ByteArrayOutputStream zipOut = new ByteArrayOutputStream(64 * 1024);
		try (ZipOutputStream zip = new ZipOutputStream(zipOut, StandardCharsets.UTF_8)) {
			zip.putNextEntry(new ZipEntry(folderName + "/"));
			zip.closeEntry();

			if (families.isEmpty()) {
				byte[] emptyPdf = renderSinglePdf(
						schoolName, logoBytes, sectionName, academicYear, levelName,
						loginUrl, copy, null, null);
				zip.putNextEntry(new ZipEntry(folderName + "/sin-alumnos.pdf"));
				zip.write(emptyPdf);
				zip.closeEntry();
			}
			else {
				Set<String> usedNames = new HashSet<>();
				for (FamilyBlock block : families.values()) {
					String studentName = block.student() == null
							? "alumno"
							: block.student().fullName();
					String fileName = uniqueFileName(studentName, usedNames);
					byte[] pdf = renderSinglePdf(
							schoolName, logoBytes, sectionName, academicYear, levelName,
							loginUrl, copy, block.student(), block.guardian());
					zip.putNextEntry(new ZipEntry(folderName + "/" + fileName));
					zip.write(pdf);
					zip.closeEntry();
				}
			}
			zip.finish();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Family handout ZIP failed: " + ex.getMessage(), ex);
		}
		return zipOut.toByteArray();
	}

	/** Folder / ZIP base name, e.g. {@code Accesos - 3ro Primaria — A}. */
	public static String folderLabel(String sectionName) {
		String section = sectionName == null || sectionName.isBlank()
				? "Seccion"
				: sectionName.trim();
		return "Accesos - " + section;
	}

	private static byte[] renderSinglePdf(
			String schoolName,
			byte[] logoBytes,
			String sectionName,
			String academicYear,
			String levelName,
			String loginUrl,
			CredentialHandoutSettings handout,
			ProvisionCredentialRow student,
			ProvisionCredentialRow guardian
	) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(24 * 1024);
		try (Document doc = new Document(PageSize.A4, 28, 28, 28, 28)) {
			PdfWriter writer = PdfWriter.getInstance(doc, out);
			writer.setPageEvent(new PageCanvas());
			doc.open();

			Type type = new Type();
			PdfPTable sheet = new PdfPTable(1);
			sheet.setWidthPercentage(100);
			sheet.setSplitLate(false);

			PdfPCell header = band(true, false);
			header.addElement(headerTable(schoolName, logoBytes, sectionName, academicYear, levelName, type));
			sheet.addCell(header);

			if (student == null) {
				PdfPCell empty = band(false, true);
				empty.addElement(paragraph("No hay cuentas en esta generación.", type.body));
				sheet.addCell(empty);
				doc.add(sheet);
				doc.close();
				return out.toByteArray();
			}

			PdfPCell intro = band(false, false);
			intro.addElement(accessIntro(handout, type));
			intro.addElement(spacer(8));
			intro.addElement(urlAndSteps(loginUrl, type));
			sheet.addCell(intro);

			PdfPCell credentials = band(false, false);
			credentials.addElement(spacer(6));
			credentials.addElement(credentialCard(
					"Ficha de Credenciales del Alumno",
					"NOMBRE COMPLETO DEL ALUMNO",
					"Estudiante registrado en el sistema",
					student,
					type));
			sheet.addCell(credentials);

			PdfPCell notice = band(false, false);
			notice.addElement(spacer(8));
			if (guardian != null) {
				notice.addElement(credentialCard(
						"Ficha de Credenciales del Tutor",
						"NOMBRE COMPLETO DEL TUTOR",
						"Apoderado vinculado al alumno",
						guardian,
						type));
			}
			else {
				notice.addElement(tutorWarning(type));
			}
			sheet.addCell(notice);

			PdfPCell foot = band(false, true);
			foot.addElement(spacer(8));
			foot.addElement(helpBox(handout, type));
			foot.addElement(spacer(10));
			foot.addElement(footer(type));
			sheet.addCell(foot);

			doc.add(sheet);
			doc.close();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Family handout PDF failed: " + ex.getMessage(), ex);
		}
		return out.toByteArray();
	}

	private static PdfPTable headerTable(
			String schoolName,
			byte[] logoBytes,
			String sectionName,
			String academicYear,
			String levelName,
			Type type
	) throws Exception {
		PdfPTable header = new PdfPTable(new float[] {1.15f, 4.1f, 2.15f});
		header.setWidthPercentage(100);

		PdfPCell logoCell = new PdfPCell();
		logoCell.setBackgroundColor(LOGO_BADGE);
		logoCell.setBorder(Rectangle.NO_BORDER);
		logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
		logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		logoCell.setPadding(8);
		logoCell.setFixedHeight(78);
		Image logo = imageOrNull(logoBytes, 58, 58);
		if (logo != null) {
			logo.setAlignment(Element.ALIGN_CENTER);
			logoCell.addElement(logo);
		}
		header.addCell(logoCell);

		PdfPCell text = new PdfPCell();
		text.setBorder(Rectangle.NO_BORDER);
		text.setVerticalAlignment(Element.ALIGN_MIDDLE);
		text.setPaddingLeft(12);
		text.setPaddingRight(8);
		Phrase kicker = new Phrase();
		kicker.add(new Phrase("DOCUMENTO OFICIAL DE ACCESO", type.kicker));
		String year = yearLine(academicYear);
		if (year != null) {
			kicker.add(new Phrase("    " + year, type.year));
		}
		text.addElement(kicker);
		Paragraph school = paragraph(nullSafe(schoolName), type.school);
		school.setSpacingBefore(3);
		text.addElement(school);
		Paragraph subtitle = paragraph(
				"Hoja de acceso familiar a la plataforma escolar", type.subtitle);
		subtitle.setSpacingBefore(2);
		text.addElement(subtitle);
		header.addCell(text);

		PdfPCell assignment = new PdfPCell();
		assignment.setBackgroundColor(SOFT);
		assignment.setBorderColor(LINE);
		assignment.setBorderWidth(0.8f);
		assignment.setPadding(8);
		assignment.setVerticalAlignment(Element.ALIGN_MIDDLE);
		assignment.addElement(paragraph("ASIGNACIÓN ESCOLAR", type.label));
		Paragraph aula = paragraph("Aula: " + nullSafe(sectionName), type.aula);
		aula.setSpacingBefore(3);
		assignment.addElement(aula);
		String level = levelLine(levelName);
		if (level != null) {
			Paragraph levelParagraph = paragraph(level, type.level);
			levelParagraph.setSpacingBefore(2);
			assignment.addElement(levelParagraph);
		}
		header.addCell(assignment);
		return header;
	}

	private static PdfPTable accessIntro(CredentialHandoutSettings handout, Type type) {
		PdfPTable row = new PdfPTable(new float[] {4.2f, 2.1f});
		row.setWidthPercentage(100);

		PdfPCell title = new PdfPCell();
		title.setBorder(Rectangle.NO_BORDER);
		title.setPadding(0);
		title.setPaddingBottom(4);
		title.addElement(paragraph(accessHeading(handout), type.sectionTitle));
		Paragraph body = paragraph(nullSafe(handout.welcomeBody()), type.body);
		body.setSpacingBefore(3);
		title.addElement(body);
		row.addCell(title);

		PdfPTable badgeTable = new PdfPTable(1);
		badgeTable.setWidthPercentage(100);
		PdfPCell badge = new PdfPCell(new Phrase("Canal seguro encriptado", type.green));
		badge.setBackgroundColor(GREEN_BG);
		badge.setBorderColor(GREEN_LINE);
		badge.setBorderWidth(0.6f);
		badge.setHorizontalAlignment(Element.ALIGN_CENTER);
		badge.setVerticalAlignment(Element.ALIGN_MIDDLE);
		badge.setPadding(6);
		badgeTable.addCell(badge);

		PdfPCell holder = new PdfPCell(badgeTable);
		holder.setBorder(Rectangle.NO_BORDER);
		holder.setVerticalAlignment(Element.ALIGN_TOP);
		holder.setPaddingTop(2);
		holder.setPaddingLeft(8);
		row.addCell(holder);
		return row;
	}

	private static PdfPTable urlAndSteps(String loginUrl, Type type) throws Exception {
		PdfPTable row = new PdfPTable(new float[] {1.15f, 1f});
		row.setWidthPercentage(100);
		row.setSpacingBefore(2);

		PdfPCell url = new PdfPCell();
		url.setBackgroundColor(DARK);
		url.setBorder(Rectangle.NO_BORDER);
		url.setPadding(12);
		url.addElement(urlHeading(type));
		Paragraph hint = paragraph(
				"Ingresa a esta dirección desde el navegador de tu celular o computadora.",
				type.onDark);
		hint.setSpacingBefore(4);
		url.addElement(hint);
		url.addElement(urlInset(loginUrl, type));
		row.addCell(url);

		PdfPCell gap = new PdfPCell();
		gap.setBorder(Rectangle.NO_BORDER);
		gap.setPaddingLeft(8);
		gap.setPaddingRight(0);
		gap.setPaddingTop(0);
		gap.setPaddingBottom(0);
		gap.addElement(stepsCard(type));
		row.addCell(gap);
		return row;
	}

	private static PdfPTable urlHeading(Type type) {
		PdfPTable head = new PdfPTable(new float[] {3.2f, 1.3f});
		head.setWidthPercentage(100);
		PdfPCell title = new PdfPCell(new Phrase("DIRECCIÓN WEB OFICIAL", type.onDarkTitle));
		title.setBorder(Rectangle.NO_BORDER);
		title.setPadding(0);
		head.addCell(title);
		PdfPCell online = new PdfPCell(new Phrase("En línea", type.online));
		online.setBorder(Rectangle.NO_BORDER);
		online.setHorizontalAlignment(Element.ALIGN_RIGHT);
		online.setPadding(0);
		head.addCell(online);
		return head;
	}

	private static PdfPTable urlInset(String loginUrl, Type type) {
		PdfPTable inset = new PdfPTable(1);
		inset.setWidthPercentage(100);
		inset.setSpacingBefore(8);
		PdfPCell cell = new PdfPCell();
		cell.setBackgroundColor(DARK_INSET);
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setPadding(8);
		cell.addElement(paragraph("URL de ingreso", type.urlLabel));
		Paragraph url = paragraph(nullSafe(loginUrl), type.url);
		url.setSpacingBefore(3);
		cell.addElement(url);
		inset.addCell(cell);
		return inset;
	}

	private static PdfPTable stepsCard(Type type) {
		PdfPTable card = new PdfPTable(1);
		card.setWidthPercentage(100);
		PdfPCell shell = new PdfPCell();
		shell.setBackgroundColor(SOFT);
		shell.setBorderColor(LINE);
		shell.setBorderWidth(0.7f);
		shell.setPadding(10);
		shell.addElement(paragraph("INSTRUCCIONES PARA EL PRIMER INICIO DE SESIÓN", type.stepHeading));

		shell.addElement(step(1,
				"Entra con los datos de abajo",
				"Ingresa a la URL e introduce el correo institucional y la contraseña temporal.",
				type));
		shell.addElement(step(2,
				"Cambia la clave obligatoriamente",
				"El sistema te solicitará una nueva contraseña personal por seguridad.",
				type));
		shell.addElement(step(3,
				"Anótala en el recuadro en blanco",
				"Escribe la nueva contraseña en esta hoja para conservarla de manera privada.",
				type));
		card.addCell(shell);
		return card;
	}

	private static PdfPTable step(int number, String title, String body, Type type) {
		PdfPTable row = new PdfPTable(new float[] {0.55f, 5.2f});
		row.setWidthPercentage(100);
		row.setSpacingBefore(7);

		PdfPTable badge = new PdfPTable(1);
		badge.setTotalWidth(16);
		badge.setLockedWidth(true);
		PdfPCell num = new PdfPCell(new Phrase(String.valueOf(number), type.stepNum));
		num.setBackgroundColor(BLUE);
		num.setBorder(Rectangle.NO_BORDER);
		num.setFixedHeight(16);
		num.setHorizontalAlignment(Element.ALIGN_CENTER);
		num.setVerticalAlignment(Element.ALIGN_MIDDLE);
		num.setPadding(0);
		badge.addCell(num);

		PdfPCell badgeCell = new PdfPCell(badge);
		badgeCell.setBorder(Rectangle.NO_BORDER);
		badgeCell.setVerticalAlignment(Element.ALIGN_TOP);
		badgeCell.setPadding(0);
		badgeCell.setPaddingTop(1);
		row.addCell(badgeCell);

		PdfPCell text = new PdfPCell();
		text.setBorder(Rectangle.NO_BORDER);
		text.setPadding(0);
		text.setPaddingLeft(6);
		text.addElement(paragraph(title, type.stepTitle));
		Paragraph detail = paragraph(body, type.stepBody);
		detail.setSpacingBefore(1);
		text.addElement(detail);
		row.addCell(text);
		return row;
	}

	private static PdfPTable credentialCard(
			String title,
			String nameLabel,
			String roleHint,
			ProvisionCredentialRow row,
			Type type
	) {
		PdfPTable card = new PdfPTable(1);
		card.setWidthPercentage(100);
		card.setKeepTogether(true);
		PdfPCell shell = new PdfPCell();
		shell.setBorderColor(LINE);
		shell.setBorderWidth(0.8f);
		shell.setPadding(10);
		shell.setBackgroundColor(Color.WHITE);

		shell.addElement(credentialHeading(title, type));
		shell.addElement(identityRow(nameLabel, roleHint, row, type));
		shell.addElement(passwordRow(row, type));
		if (hasTemporaryPassword(row)) {
			shell.addElement(writeInBox(type));
		}
		card.addCell(shell);
		return card;
	}

	private static PdfPTable credentialHeading(String title, Type type) {
		PdfPTable row = new PdfPTable(new float[] {4f, 1.7f});
		row.setWidthPercentage(100);
		PdfPCell titleCell = new PdfPCell(new Phrase(title, type.cardTitle));
		titleCell.setBorder(Rectangle.NO_BORDER);
		titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		titleCell.setPadding(0);
		titleCell.setPaddingBottom(8);
		row.addCell(titleCell);

		PdfPCell badge = new PdfPCell(new Phrase("Uso estricto personal", type.amber));
		badge.setBackgroundColor(AMBER_BG);
		badge.setBorderColor(AMBER_LINE);
		badge.setHorizontalAlignment(Element.ALIGN_CENTER);
		badge.setVerticalAlignment(Element.ALIGN_MIDDLE);
		badge.setPadding(4);
		row.addCell(badge);
		return row;
	}

	private static PdfPTable identityRow(
			String nameLabel,
			String roleHint,
			ProvisionCredentialRow row,
			Type type
	) {
		PdfPTable rowTable = new PdfPTable(new float[] {1f, 1f});
		rowTable.setWidthPercentage(100);
		rowTable.setSpacingBefore(2);
		rowTable.addCell(field(nameLabel, nullSafe(row.fullName()), roleHint, type));
		rowTable.addCell(field(
				"CORREO / USUARIO INSTITUCIONAL",
				nullSafe(row.email()),
				null,
				type));
		return rowTable;
	}

	private static PdfPCell field(String label, String value, String hint, Type type) {
		PdfPCell cell = new PdfPCell();
		cell.setBackgroundColor(SOFT);
		cell.setBorderColor(LINE);
		cell.setBorderWidth(0.6f);
		cell.setPadding(8);
		cell.addElement(paragraph(label, type.label));
		Paragraph valueParagraph = paragraph(value, type.name);
		valueParagraph.setSpacingBefore(2);
		cell.addElement(valueParagraph);
		if (hint != null && !hint.isBlank()) {
			Paragraph hintParagraph = paragraph(hint, type.muted);
			hintParagraph.setSpacingBefore(1);
			cell.addElement(hintParagraph);
		}
		return cell;
	}

	private static PdfPTable passwordRow(ProvisionCredentialRow row, Type type) {
		PdfPTable rowTable = new PdfPTable(new float[] {1.35f, 1f});
		rowTable.setWidthPercentage(100);
		rowTable.setSpacingBefore(8);

		PdfPCell label = new PdfPCell();
		label.setBorder(Rectangle.NO_BORDER);
		label.setVerticalAlignment(Element.ALIGN_MIDDLE);
		label.setPadding(2);
		label.setPaddingRight(8);
		if (hasTemporaryPassword(row)) {
			label.addElement(paragraph("CONTRASEÑA TEMPORAL DE PRIMER INGRESO", type.passwordLabel));
			Paragraph hint = paragraph(
					"Respeta mayúsculas, minúsculas y caracteres especiales.",
					type.muted);
			hint.setSpacingBefore(2);
			label.addElement(hint);
		}
		else {
			label.addElement(paragraph(
					"Cuenta ya existente. No se reimprime la contraseña. "
							+ "Usa la clave que ya tenías o acude a secretaría.",
					type.muted));
		}
		rowTable.addCell(label);

		PdfPCell value = new PdfPCell();
		value.setBorder(Rectangle.NO_BORDER);
		value.setVerticalAlignment(Element.ALIGN_MIDDLE);
		value.setPadding(0);
		if (hasTemporaryPassword(row)) {
			value.addElement(passwordPill(row.temporaryPassword(), type));
		}
		rowTable.addCell(value);
		return rowTable;
	}

	private static PdfPTable passwordPill(String password, Type type) {
		PdfPTable pill = new PdfPTable(1);
		pill.setWidthPercentage(100);
		PdfPCell cell = new PdfPCell(new Phrase(password, type.password));
		cell.setBackgroundColor(PILL_BG);
		cell.setBorderColor(PILL_LINE);
		cell.setBorderWidth(0.8f);
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPaddingTop(8);
		cell.setPaddingBottom(8);
		cell.setPaddingLeft(6);
		cell.setPaddingRight(6);
		pill.addCell(cell);
		return pill;
	}

	private static PdfPTable writeInBox(Type type) {
		PdfPTable box = new PdfPTable(1);
		box.setWidthPercentage(100);
		box.setSpacingBefore(8);

		PdfPTable caption = new PdfPTable(new float[] {2.4f, 1.4f});
		caption.setWidthPercentage(100);
		PdfPCell left = new PdfPCell(new Phrase(
				"NUEVA CONTRASEÑA (ANOTAR DESPUÉS DEL PRIMER INGRESO)",
				type.label));
		left.setBorder(Rectangle.NO_BORDER);
		left.setPadding(0);
		left.setPaddingBottom(4);
		caption.addCell(left);
		PdfPCell right = new PdfPCell(new Phrase("Espacio seguro para uso familiar", type.muted));
		right.setBorder(Rectangle.NO_BORDER);
		right.setHorizontalAlignment(Element.ALIGN_RIGHT);
		right.setPadding(0);
		right.setPaddingBottom(4);
		caption.addCell(right);

		PdfPCell captionCell = new PdfPCell(caption);
		captionCell.setBorder(Rectangle.NO_BORDER);
		captionCell.setPadding(0);
		captionCell.setPaddingBottom(4);
		box.addCell(captionCell);

		PdfPCell area = new PdfPCell(new Phrase(
				"Escribe aquí tu nueva contraseña una vez cambiada en la plataforma.",
				type.placeholder));
		area.setBorder(Rectangle.NO_BORDER);
		area.setCellEvent(new DashedOutline());
		area.setFixedHeight(46);
		area.setPadding(8);
		area.setVerticalAlignment(Element.ALIGN_MIDDLE);
		box.addCell(area);

		PdfPCell keep = new PdfPCell(new Phrase(
				"Mantén esta hoja en un lugar seguro dentro del hogar.",
				type.muted));
		keep.setBorder(Rectangle.NO_BORDER);
		keep.setHorizontalAlignment(Element.ALIGN_RIGHT);
		keep.setPadding(0);
		keep.setPaddingTop(3);
		box.addCell(keep);
		return box;
	}

	private static PdfPTable tutorWarning(Type type) {
		PdfPTable box = new PdfPTable(1);
		box.setWidthPercentage(100);
		PdfPCell cell = new PdfPCell();
		cell.setBackgroundColor(AMBER_BG);
		cell.setBorderColor(AMBER_LINE);
		cell.setBorderWidth(0.7f);
		cell.setPadding(8);
		Phrase text = new Phrase();
		text.add(new Phrase("AVISO IMPORTANTE DEL TUTOR: ", type.warnTitle));
		text.add(new Phrase(
				"No hay apoderado vinculado en el padrón. "
						+ "Pide a secretaría que lo registre antes de entregar esta hoja.",
				type.warnBody));
		cell.addElement(text);
		box.addCell(cell);
		return box;
	}

	private static PdfPTable helpBox(CredentialHandoutSettings handout, Type type) {
		PdfPTable box = new PdfPTable(1);
		box.setWidthPercentage(100);
		PdfPCell cell = new PdfPCell();
		cell.setBackgroundColor(SOFT);
		cell.setBorderColor(LINE);
		cell.setBorderWidth(0.7f);
		cell.setPadding(8);
		cell.addElement(paragraph("¿Problemas con el acceso?", type.cardTitle));
		String note = handout.supportNote() == null ? "" : handout.supportNote().trim();
		if (handout.supportPhone() != null && !handout.supportPhone().isBlank()) {
			String phone = "Contacto del colegio: " + handout.supportPhone().trim();
			note = note.isBlank() ? phone : note + " " + phone;
		}
		if (!note.isBlank()) {
			Paragraph body = paragraph(note, type.body);
			body.setSpacingBefore(2);
			cell.addElement(body);
		}
		box.addCell(cell);
		return box;
	}

	private static PdfPTable footer(Type type) {
		PdfPTable row = new PdfPTable(new float[] {1.2f, 2.2f});
		row.setWidthPercentage(100);
		PdfPCell left = new PdfPCell(new Phrase("EduShift", type.footer));
		left.setBorder(Rectangle.NO_BORDER);
		left.setPadding(0);
		left.setPaddingTop(4);
		row.addCell(left);
		PdfPCell right = new PdfPCell(new Phrase(
				"Documento emitido para entrega exclusiva a padres o tutores legales.",
				type.footer));
		right.setBorder(Rectangle.NO_BORDER);
		right.setHorizontalAlignment(Element.ALIGN_RIGHT);
		right.setPadding(0);
		right.setPaddingTop(4);
		row.addCell(right);
		return row;
	}

	private static String accessHeading(CredentialHandoutSettings handout) {
		String title = handout.welcomeTitle();
		String defaults = CredentialHandoutSettings.defaults().welcomeTitle();
		if (title == null || title.isBlank() || title.equals(defaults)) {
			return "ACCESO A LA PLATAFORMA EDUSHIFT";
		}
		return title.toUpperCase(Locale.ROOT);
	}

	private static String yearLine(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String name = raw.trim();
		if (name.toLowerCase(Locale.ROOT).startsWith("año")) {
			return name;
		}
		return "Año Escolar " + name;
	}

	private static String levelLine(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String name = raw.trim();
		if (name.regionMatches(true, 0, "nivel", 0, 5)) {
			return name;
		}
		if (name.length() > 1 && name.equals(name.toUpperCase(Locale.ROOT))) {
			name = name.substring(0, 1).toUpperCase(Locale.ROOT)
					+ name.substring(1).toLowerCase(Locale.ROOT);
		}
		return "Nivel " + name;
	}

	private static boolean hasTemporaryPassword(ProvisionCredentialRow row) {
		return row != null
				&& "CREATED".equalsIgnoreCase(row.status())
				&& row.temporaryPassword() != null
				&& !row.temporaryPassword().isBlank();
	}

	private static PdfPCell band(boolean first, boolean last) {
		PdfPCell cell = new PdfPCell();
		cell.setBackgroundColor(Color.WHITE);
		int borders = Rectangle.LEFT | Rectangle.RIGHT;
		if (first) {
			borders |= Rectangle.TOP;
		}
		if (last) {
			borders |= Rectangle.BOTTOM;
		}
		cell.setBorder(borders);
		cell.setBorderColor(LINE);
		cell.setBorderWidth(0.8f);
		cell.setPaddingLeft(16);
		cell.setPaddingRight(16);
		if (first) {
			cell.setBorderWidthTop(3.5f);
			cell.setBorderColorTop(BLUE);
			cell.setPaddingTop(16);
			cell.setPaddingBottom(6);
		}
		else if (last) {
			cell.setPaddingTop(2);
			cell.setPaddingBottom(14);
		}
		else {
			cell.setPaddingTop(2);
			cell.setPaddingBottom(2);
		}
		return cell;
	}

	private static Map<UUID, FamilyBlock> groupFamilies(List<ProvisionCredentialRow> rows) {
		Map<UUID, FamilyBlock> map = new LinkedHashMap<>();
		if (rows == null) {
			return map;
		}
		for (ProvisionCredentialRow row : rows) {
			if (row == null || row.studentPublicUuid() == null) {
				continue;
			}
			UUID sid = row.studentPublicUuid();
			FamilyBlock block = map.computeIfAbsent(sid, id -> new FamilyBlock());
			if ("STUDENT".equalsIgnoreCase(row.role())) {
				block.student = row;
			}
			else if ("PARENT".equalsIgnoreCase(row.role()) && block.guardian == null) {
				block.guardian = row;
			}
		}
		List<UUID> drop = new ArrayList<>();
		for (Map.Entry<UUID, FamilyBlock> entry : map.entrySet()) {
			if (entry.getValue().student == null) {
				drop.add(entry.getKey());
			}
		}
		drop.forEach(map::remove);
		return map;
	}

	private static String uniqueFileName(String studentName, Set<String> used) {
		String base = sanitizePathSegment(studentName);
		if (base.isBlank() || base.equals("_")) {
			base = "alumno";
		}
		String candidate = base + ".pdf";
		int i = 2;
		while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
			candidate = base + " (" + i + ").pdf";
			i++;
		}
		return candidate;
	}

	/** Safe folder / file segment for ZIP (Windows + ZIP reserved). */
	static String sanitizePathSegment(String raw) {
		if (raw == null || raw.isBlank()) {
			return "_";
		}
		String cleaned = raw.trim()
				.replaceAll("[\\\\/:*?\"<>|]", "-")
				.replaceAll("\\s+", " ")
				.replaceAll("\\.+", ".")
				.trim();
		if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
			return "_";
		}
		if (cleaned.length() > 120) {
			cleaned = cleaned.substring(0, 120).trim();
		}
		return cleaned;
	}

	private static byte[] tryLoadLogoBytes(String logoUrl) {
		byte[] remote = PlatformPdfBrand.fetchLogo(logoUrl);
		return remote != null ? remote : PlatformBrandAssets.markBytes();
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
			log.debug("[handout-pdf] logo decode failed: {}", ex.toString());
			return null;
		}
	}

	private static Paragraph paragraph(String text, Font font) {
		return new Paragraph(text == null ? "" : text, font);
	}

	private static Paragraph spacer(float leading) {
		Paragraph paragraph = new Paragraph(" ");
		paragraph.setLeading(leading);
		return paragraph;
	}

	private static String nullSafe(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}

	private static final class FamilyBlock {
		private ProvisionCredentialRow student;
		private ProvisionCredentialRow guardian;

		ProvisionCredentialRow student() {
			return student;
		}

		ProvisionCredentialRow guardian() {
			return guardian;
		}
	}

	private static final class DashedOutline implements PdfPCellEvent {
		@Override
		public void cellLayout(PdfPCell cell, Rectangle rect, PdfContentByte[] canvas) {
			PdfContentByte cb = canvas[PdfPTable.LINECANVAS];
			cb.saveState();
			cb.setColorStroke(new Color(0xCB, 0xD5, 0xE1));
			cb.setLineWidth(0.9f);
			cb.setLineDash(3f, 2.5f);
			float inset = 1f;
			cb.rectangle(
					rect.getLeft() + inset,
					rect.getBottom() + inset,
					rect.getWidth() - (inset * 2),
					rect.getHeight() - (inset * 2));
			cb.stroke();
			cb.restoreState();
		}
	}

	private static final class PageCanvas extends PdfPageEventHelper {
		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			PdfContentByte cb = writer.getDirectContentUnder();
			cb.saveState();
			cb.setColorFill(PAGE);
			Rectangle page = document.getPageSize();
			cb.rectangle(0, 0, page.getWidth(), page.getHeight());
			cb.fill();
			cb.restoreState();
		}
	}

	private static final class Type {
		private final Font kicker = font(FontFactory.HELVETICA_BOLD, 8, BLUE);
		private final Font year = font(FontFactory.HELVETICA, 8, SLATE);
		private final Font school = font(FontFactory.HELVETICA_BOLD, 16, INK);
		private final Font subtitle = font(FontFactory.HELVETICA, 9, SLATE);
		private final Font label = font(FontFactory.HELVETICA_BOLD, 7, SLATE);
		private final Font aula = font(FontFactory.HELVETICA_BOLD, 11, INK);
		private final Font level = font(FontFactory.HELVETICA_BOLD, 9, BLUE);
		private final Font sectionTitle = font(FontFactory.HELVETICA_BOLD, 11, BLUE);
		private final Font body = font(FontFactory.HELVETICA, 9, INK);
		private final Font green = font(FontFactory.HELVETICA_BOLD, 8, GREEN);
		private final Font onDarkTitle = font(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
		private final Font onDark = font(FontFactory.HELVETICA, 8, MIST);
		private final Font online = font(FontFactory.HELVETICA_BOLD, 8, new Color(0x4A, 0xDE, 0x80));
		private final Font urlLabel = font(FontFactory.HELVETICA, 7, MIST);
		private final Font url = font(FontFactory.HELVETICA_BOLD, 9, CYAN);
		private final Font stepHeading = font(FontFactory.HELVETICA_BOLD, 8, SLATE);
		private final Font stepNum = font(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
		private final Font stepTitle = font(FontFactory.HELVETICA_BOLD, 9, INK);
		private final Font stepBody = font(FontFactory.HELVETICA, 8, SLATE);
		private final Font cardTitle = font(FontFactory.HELVETICA_BOLD, 11, INK);
		private final Font amber = font(FontFactory.HELVETICA_BOLD, 7, AMBER);
		private final Font name = font(FontFactory.HELVETICA_BOLD, 11, INK);
		private final Font muted = font(FontFactory.HELVETICA, 8, SLATE);
		private final Font passwordLabel = font(FontFactory.HELVETICA_BOLD, 7, AMBER);
		private final Font password = font(FontFactory.COURIER_BOLD, 11, PILL_INK);
		private final Font placeholder = font(FontFactory.HELVETICA_OBLIQUE, 8, PLACEHOLDER);
		private final Font warnTitle = font(FontFactory.HELVETICA_BOLD, 8, AMBER);
		private final Font warnBody = font(FontFactory.HELVETICA, 8, new Color(0x92, 0x40, 0x0E));
		private final Font footer = font(FontFactory.HELVETICA, 8, SLATE);

		private static Font font(String face, float size, Color color) {
			return FontFactory.getFont(face, size, Font.NORMAL, color);
		}
	}
}
