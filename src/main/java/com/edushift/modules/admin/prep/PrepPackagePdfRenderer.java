package com.edushift.modules.admin.prep;

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
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Institutional PDFs for the school data-prep pack.
 * Layout follows the onboarding brief: brand bar, responsibility matrix,
 * numbered blocks and a page footer. Copy stays in school language.
 */
@Component
public class PrepPackagePdfRenderer {

	private static final Color NAVY = new Color(0x1B, 0x3A, 0x4B);
	private static final Color INK = new Color(0x1E, 0x29, 0x3B);
	private static final Color MUTED = new Color(0x64, 0x74, 0x8B);
	private static final Color LINE = new Color(0xE2, 0xE8, 0xF0);
	private static final Color HEAD = new Color(0xF1, 0xF5, 0xF9);
	private static final Color ZEBRAS = new Color(0xF8, 0xFA, 0xFC);
	private static final Color YES_BG = new Color(0xF0, 0xFD, 0xF4);
	private static final Color NO_BG = new Color(0xFE, 0xF2, 0xF2);
	private static final Color REQ = new Color(0xBE, 0x12, 0x30);
	private static final Color OPT = new Color(0x47, 0x55, 0x69);
	private static final Color REC = new Color(0x1D, 0x4E, 0xD8);
	private static final String DOC_CODE = "EDU-PREP-001";
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.of("es", "PE"));

	byte[] renderGuide(String schoolName, PrepPackageVariant variant) {
		return open(schoolName, "Guía de preparación", doc -> {
			title(doc, "Preparación de Datos y Parametrización Institucional",
					schoolName + "  ·  Variante " + variant.labelEs());
			body(doc, "Este expediente reúne lo que el colegio debe completar y devolver para dejar "
					+ schoolName + " listo en EduShift. El colegio queda en español, con hora de Lima y montos en soles.");
			section(doc, "MATRIZ DE RESPONSABLES: QUIÉN ENTREGA QUÉ");
			responsibilityMatrix(doc, variant);
			section(doc, "BLOQUE A  —  ALCANCE");
			scopeColumns(doc);
			section(doc, "BLOQUE B  —  FICHA DEL COLEGIO");
			note(doc, "Hoja plantillas/01-institucion.xlsx. Reemplace la fila de ejemplo y no borre los títulos.");
			fieldTable(doc, List.of(
					new String[] { "Nombre oficial", "Tal como figura en los documentos del colegio.", "Obligatorio" },
					new String[] { "Nombre corto para la web", "Minúsculas y guiones. Ejemplo: colegio-san-marcos.", "Obligatorio" },
					new String[] { "Correo de quien administrará", "Recibirá el acceso de dirección.", "Obligatorio" },
					new String[] { "Nombres y apellidos", "De la persona que administrará el colegio.", "Obligatorio" },
					new String[] { "Color principal", "Código como #1E3A5F, o una descripción (azul marino).", "Obligatorio" },
					new String[] { "Color secundario", "Solo si quieren un segundo color.", "Opcional" },
					new String[] { "Teléfono de soporte", "Aparece en las hojas de acceso de las familias.", "Opcional" }
			));
			section(doc, "BLOQUE C  —  ARCHIVOS DE IMAGEN");
			fileTable(doc);
			section(doc, "BLOQUE D  —  AÑO, SECCIONES Y CURSOS");
			note(doc, "Las fechas del calendario indican qué periodo está en curso cuando docentes y familias entran.");
			bulletTable(doc, List.of(
					new String[] { "02-calendario-academico.xlsx", "Una fila por bimestre, trimestre o por el año completo. Tipo: BIMESTRE, TRIMESTRE o ANUAL. El número de orden empieza en 1." },
					new String[] { "03-secciones.xlsx", "Nivel, grado y letra (A, B, C). En la plataforma se ve como 1.º · A. Cupo y tutor son opcionales. El tutor usa el mismo documento que en la hoja de docentes." },
					new String[] { "04-cursos.xlsx", "Código, nombre y niveles (Inicial, Primaria, Secundaria). No envíe competencias ni unidades: se crean después en la ficha del curso." }
			));
			note(doc, "Cuando el curso ya esté cargado, el administrador abre Matemática y Comunicación y usa Cargar programa del MINEDU. En los demás cursos, el coordinador las escribe en la ficha.");
			section(doc, "BLOQUE E  —  DOCENTES, ESTUDIANTES Y APODERADOS");
			bulletTable(doc, List.of(
					new String[] { "05-docentes.xlsx", "Es la misma hoja que luego se sube en la plataforma. El correo es necesario para invitarlos." },
					new String[] { "06-estudiantes-apoderados.xlsx", "Reemplace la fila de ejemplo. La sección (por ejemplo 1.º de primaria · A) se elige al subir el archivo, no en el Excel." }
			));
			section(doc, "BLOQUE F  —  QUÉ DOCENTE DICTA CADA CURSO");
			note(doc, "Hoja plantillas/07-asignaciones-docente.xlsx. Una fila significa: este docente dicta este curso en esta sección durante este periodo. Repita la fila por cada bimestre o trimestre. Sin estas filas el docente no verá sus cursos al preparar clases.");
			if (variant == PrepPackageVariant.PRODUCTION) {
				section(doc, "BLOQUE G  —  PAGOS");
				note(doc, "Hoja plantillas/08-conceptos-pago.xlsx. Tipo en español: Matrícula, Pensión, Útiles, Alimentación, Movilidad, Examen, Evento u Otro. El monto va en soles (por ejemplo 350.00).");
			}
			section(doc, "CÓMO DEVOLVER EL PAQUETE");
			bulletTable(doc, List.of(
					new String[] { "1", "Complete las hojas. No borre la fila de títulos y reemplace la fila de ejemplo." },
					new String[] { "2", "Adjunte el logo y, si los tiene, el fondo de ingreso y el icono de la pestaña." },
					new String[] { "3", "Envíe esta carpeta a su contacto de EduShift. El detalle está en LEEME.txt." }
			));
			signature(doc, schoolName);
		});
	}

	byte[] renderAnnex(String schoolName, PrepPackageVariant variant) {
		boolean pilot = variant == PrepPackageVariant.PILOT;
		String heading = pilot ? "Anexo Piloto" : "Anexo Producción";
		return open(schoolName, heading, doc -> {
			title(doc, heading, schoolName);
			body(doc, pilot
					? "Lo mínimo para la prueba en " + schoolName + "."
					: "Padrón completo para empezar a usar EduShift en " + schoolName + ".");
			section(doc, pilot ? "QUÉ ENVIAR EN LA PRUEBA" : "QUÉ ENVIAR");
			if (pilot) {
				bulletTable(doc, List.of(
						new String[] { "Año y periodos", "Fechas claras del periodo de la prueba." },
						new String[] { "Secciones", "Solo las que se van a probar, con grado y letra." },
						new String[] { "Docentes", "De 2 a 5, con correo real." },
						new String[] { "Estudiantes", "Los de esas secciones. Si usarán el portal, al menos un apoderado con correo." },
						new String[] { "Cursos", "Los de la prueba. Matemática y Comunicación permiten cargar después el programa del MINEDU." },
						new String[] { "Asignaciones", "Qué docente dicta cada curso en el periodo de la prueba." },
						new String[] { "Identidad", "Nombre del colegio y color principal. El logo es opcional en esta prueba." },
						new String[] { "Pagos", "No incluya conceptos de pago." }
				));
			}
			else {
				bulletTable(doc, List.of(
						new String[] { "Secciones", "Todos los grados y secciones del año lectivo." },
						new String[] { "Docentes", "Docentes y personal, con correo para la invitación." },
						new String[] { "Familias", "Estudiantes y apoderados. El correo del apoderado hace falta si usarán el portal." },
						new String[] { "Cursos y asignaciones", "Cursos del año, y qué docente dicta cada uno en cada periodo." },
						new String[] { "Imagen", "Logo, color principal y, si lo tienen, fondo de la pantalla de ingreso." },
						new String[] { "Pagos", "plantillas/08-conceptos-pago.xlsx, con el monto en soles." }
				));
				section(doc, "ANTES DE ACTIVAR EL COLEGIO");
				bulletTable(doc, List.of(
						new String[] { "Documentos", "Cada documento aparece una sola vez en su lista." },
						new String[] { "Correos", "Los de docentes y de apoderados con portal están bien escritos." },
						new String[] { "Fechas", "Las del año y de los periodos no se pisan. Al menos un periodo cubre el día de inicio." },
						new String[] { "Cupo", "El de cada sección alcanza para sus alumnos." },
						new String[] { "Después de cargar", "El administrador abre Matemática y Comunicación y usa Cargar programa del MINEDU. Las unidades se crean en la plataforma." }
				));
			}
			signature(doc, schoolName);
		});
	}

	byte[] renderChecklist(String schoolName) {
		return open(schoolName, "Checklist de archivos", doc -> {
			title(doc, "Checklist de archivos", "Adjuntos de " + schoolName);
			body(doc, "Imágenes y hojas que acompañan el paquete. El detalle de datos está en la guía.");
			section(doc, "BLOQUE C  —  ARCHIVOS DE IMAGEN");
			fileTable(doc);
			section(doc, "HOJAS EXCEL");
			bulletTable(doc, List.of(
					new String[] { "Estudiantes", "Se sube tal cual en EduShift, hasta 10 MB." },
					new String[] { "El resto", "Se completa y se envía en este paquete. No cambie los títulos de las columnas." }
			));
			section(doc, "QUÉ NO ADJUNTAR");
			bulletTable(doc, List.of(
					new String[] { "Claves", "Contraseñas ni archivos protegidos con clave." },
					new String[] { "Compresión", "Carpetas comprimidas dentro de este paquete." }
			));
		});
	}

	private void responsibilityMatrix(Document doc, PrepPackageVariant variant) throws Exception {
		PdfPTable table = grid(new float[] { 28f, 48f, 24f });
		headerRow(table, "Dependencia", "Información a completar", "Responsable sugerido");
		dataRow(table, 0, "Dirección", "Nombre oficial, nombre corto, color y correo de quien administrará.", "Director(a)");
		dataRow(table, 1, "Secretaría", "Listas de docentes, estudiantes y apoderados.", "Secretaria");
		dataRow(table, 2, "Coordinación académica", "Año, periodos, secciones, cursos y qué docente dicta cada curso.", "Coordinador(a)");
		if (variant == PrepPackageVariant.PRODUCTION) {
			dataRow(table, 3, "Administración", "Conceptos de pago, con el monto en soles.", "Administración");
		}
		doc.add(table);
	}

	private void scopeColumns(Document doc) throws Exception {
		PdfPTable table = grid(new float[] { 50f, 50f });
		table.setSpacingBefore(2f);
		table.addCell(scopeCell("Qué sí enviar",
				"Hojas Excel de la carpeta plantillas, con la fila de ejemplo reemplazada.\n"
						+ "Logo del colegio (recomendado).\n"
						+ "Fondo de ingreso e icono, si los tienen.",
				YES_BG));
		table.addCell(scopeCell("Qué no adjuntar",
				"Contraseñas ni archivos con clave.\n"
						+ "Competencias y unidades: se crean después en la plataforma.\n"
						+ "Carpetas comprimidas dentro de este paquete.",
				NO_BG));
		doc.add(table);
	}

	private void fileTable(Document doc) throws Exception {
		PdfPTable table = grid(new float[] { 28f, 42f, 30f });
		headerRow(table, "Archivo", "Formato", "Estado");
		dataRow(table, 0, "Logo", "PNG, JPEG, WebP, SVG o GIF. De preferencia fondo transparente, al menos 512 px. Máximo 25 MB.", "Recomendado");
		dataRow(table, 1, "Fondo de ingreso", "Los mismos formatos, foto horizontal (como 1920×1080). Máximo 25 MB.", "Opcional");
		dataRow(table, 2, "Icono de la pestaña", "PNG, SVG o ICO, cuadrado. Máximo 25 MB.", "Opcional");
		doc.add(table);
	}

	private void fieldTable(Document doc, List<String[]> rows) throws Exception {
		PdfPTable table = grid(new float[] { 32f, 48f, 20f });
		headerRow(table, "Dato", "Cómo completarlo", "Estado");
		for (int i = 0; i < rows.size(); i++) {
			String[] row = rows.get(i);
			table.addCell(textCell(row[0], true, i));
			table.addCell(textCell(row[1], false, i));
			table.addCell(badgeCell(row[2]));
		}
		doc.add(table);
	}

	private void bulletTable(Document doc, List<String[]> rows) throws Exception {
		PdfPTable table = grid(new float[] { 32f, 68f });
		for (int i = 0; i < rows.size(); i++) {
			table.addCell(textCell(rows.get(i)[0], true, i));
			table.addCell(textCell(rows.get(i)[1], false, i));
		}
		doc.add(table);
	}

	private void signature(Document doc, String schoolName) throws Exception {
		PdfPTable table = grid(new float[] { 50f, 50f });
		table.setSpacingBefore(8f);
		table.addCell(signCell("Colegio", schoolName));
		table.addCell(signCell("Responsable y fecha", "Nombre: ________________    Fecha: __________"));
		doc.add(table);
	}

	private static void title(Document doc, String heading, String subtitle) throws Exception {
		Paragraph h = new Paragraph(heading, font(16, Font.BOLD, NAVY));
		h.setSpacingAfter(2f);
		doc.add(h);
		Paragraph sub = new Paragraph(subtitle, font(9, Font.NORMAL, MUTED));
		sub.setSpacingAfter(6f);
		doc.add(sub);
		metaStrip(doc);
	}

	private static void metaStrip(Document doc) throws Exception {
		String today = LocalDate.now(ZoneId.of("America/Lima")).format(DAY);
		PdfPTable strip = grid(new float[] { 25f, 20f, 25f, 30f });
		strip.addCell(metaCell("Código", DOC_CODE));
		strip.addCell(metaCell("Versión", "1.0"));
		strip.addCell(metaCell("Fecha", today));
		strip.addCell(metaCell("Uso", "Interno del colegio"));
		doc.add(strip);
	}

	private static void section(Document doc, String label) throws Exception {
		PdfPTable bar = new PdfPTable(1);
		bar.setWidthPercentage(100);
		bar.setSpacingBefore(12f);
		bar.setSpacingAfter(6f);
		PdfPCell cell = new PdfPCell(new Phrase(label, font(9, Font.BOLD, Color.WHITE)));
		cell.setBackgroundColor(NAVY);
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setPaddingTop(6f);
		cell.setPaddingBottom(6f);
		cell.setPaddingLeft(8f);
		bar.addCell(cell);
		doc.add(bar);
	}

	private static void body(Document doc, String text) throws Exception {
		Paragraph p = new Paragraph(text, font(9.5f, Font.NORMAL, INK));
		p.setLeading(13f);
		p.setSpacingAfter(4f);
		doc.add(p);
	}

	private static void note(Document doc, String text) throws Exception {
		Paragraph p = new Paragraph(text, font(9, Font.NORMAL, INK));
		p.setLeading(12.5f);
		p.setSpacingAfter(4f);
		doc.add(p);
	}

	private static PdfPTable grid(float[] widths) throws Exception {
		PdfPTable table = new PdfPTable(widths.length);
		table.setWidthPercentage(100);
		table.setWidths(widths);
		table.setSpacingAfter(2f);
		return table;
	}

	private static void headerRow(PdfPTable table, String... labels) {
		for (String label : labels) {
			PdfPCell cell = new PdfPCell(new Phrase(label, font(8, Font.BOLD, MUTED)));
			cell.setBackgroundColor(HEAD);
			cell.setBorderColor(LINE);
			cell.setPadding(5f);
			table.addCell(cell);
		}
	}

	private static void dataRow(PdfPTable table, int index, String... values) {
		for (int i = 0; i < values.length; i++) {
			boolean last = i == values.length - 1 && isBadge(values[i]);
			table.addCell(last ? badgeCell(values[i]) : textCell(values[i], i == 0, index));
		}
	}

	private static boolean isBadge(String value) {
		return "Obligatorio".equals(value) || "Opcional".equals(value) || "Recomendado".equals(value);
	}

	private static PdfPCell textCell(String text, boolean strong, int index) {
		PdfPCell cell = new PdfPCell(new Phrase(text, font(8.5f, strong ? Font.BOLD : Font.NORMAL, INK)));
		cell.setBackgroundColor(index % 2 == 0 ? Color.WHITE : ZEBRAS);
		cell.setBorderColor(LINE);
		cell.setPadding(5f);
		cell.setLeading(0f, 1.25f);
		return cell;
	}

	private static PdfPCell badgeCell(String label) {
		Color color = switch (label) {
			case "Obligatorio" -> REQ;
			case "Recomendado" -> REC;
			default -> OPT;
		};
		PdfPCell cell = new PdfPCell(new Phrase(label, font(8, Font.BOLD, color)));
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setBorderColor(LINE);
		cell.setPadding(5f);
		cell.setBackgroundColor(Color.WHITE);
		return cell;
	}

	private static PdfPCell scopeCell(String heading, String text, Color background) {
		Phrase phrase = new Phrase();
		phrase.add(new Phrase(heading + "\n", font(9, Font.BOLD, NAVY)));
		phrase.add(new Phrase(text, font(8.5f, Font.NORMAL, INK)));
		PdfPCell cell = new PdfPCell(phrase);
		cell.setBackgroundColor(background);
		cell.setBorderColor(LINE);
		cell.setPadding(8f);
		cell.setLeading(0f, 1.3f);
		return cell;
	}

	private static PdfPCell metaCell(String label, String value) {
		Phrase phrase = new Phrase();
		phrase.add(new Phrase(label + "\n", font(7, Font.NORMAL, MUTED)));
		phrase.add(new Phrase(value, font(8, Font.BOLD, INK)));
		PdfPCell cell = new PdfPCell(phrase);
		cell.setBackgroundColor(HEAD);
		cell.setBorderColor(LINE);
		cell.setPadding(5f);
		return cell;
	}

	private static PdfPCell signCell(String label, String value) {
		Phrase phrase = new Phrase();
		phrase.add(new Phrase(label + "\n", font(7, Font.BOLD, MUTED)));
		phrase.add(new Phrase(value, font(9, Font.NORMAL, INK)));
		PdfPCell cell = new PdfPCell(phrase);
		cell.setBorderColor(LINE);
		cell.setPadding(8f);
		cell.setMinimumHeight(36f);
		return cell;
	}

	private static Font font(float size, int style, Color color) {
		return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
	}

	private byte[] open(String schoolName, String footerLabel, DocBody body) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(32 * 1024);
		Document doc = new Document(PageSize.A4, 40, 40, 64, 48);
		try {
			PdfWriter writer = PdfWriter.getInstance(doc, out);
			writer.setPageEvent(new Chrome(schoolName, footerLabel));
			doc.open();
			body.write(doc);
			doc.close();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Prep package PDF failed: " + ex.getMessage(), ex);
		}
		return out.toByteArray();
	}

	@FunctionalInterface
	private interface DocBody {
		void write(Document doc) throws Exception;
	}

	private static final class Chrome extends PdfPageEventHelper {
		private final String schoolName;
		private final String footerLabel;
		private final byte[] mark = PlatformBrandAssets.markBytes();
		private PdfTemplate total;

		private Chrome(String schoolName, String footerLabel) {
			this.schoolName = schoolName;
			this.footerLabel = footerLabel;
		}

		@Override
		public void onOpenDocument(PdfWriter writer, Document document) {
			total = writer.getDirectContent().createTemplate(24, 12);
		}

		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			PdfContentByte cb = writer.getDirectContent();
			float left = document.left();
			float right = document.right();
			float headerY = document.top() + 22f;

			cb.setColorStroke(NAVY);
			cb.setLineWidth(2f);
			cb.moveTo(left, document.top() + 10f);
			cb.lineTo(right, document.top() + 10f);
			cb.stroke();

			Image logo = imageOrNull(mark, 16, 16);
			float textX = left;
			if (logo != null) {
				try {
					logo.setAbsolutePosition(left, headerY - 2f);
					cb.addImage(logo);
					textX = left + 22f;
				}
				catch (Exception ex) {
					textX = left;
				}
			}
			ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
					new Phrase("EduShift", font(9, Font.BOLD, NAVY)), textX, headerY + 4f, 0);
			ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
					new Phrase("Preparación institucional", font(7.5f, Font.NORMAL, MUTED)), textX, headerY - 6f, 0);
			ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
					new Phrase(DOC_CODE + "   ·   v1.0", font(7.5f, Font.NORMAL, MUTED)), right, headerY, 0);

			float foot = document.bottom() - 18f;
			cb.setColorStroke(LINE);
			cb.setLineWidth(0.6f);
			cb.moveTo(left, document.bottom() - 6f);
			cb.lineTo(right, document.bottom() - 6f);
			cb.stroke();

			String leftLabel = trim(schoolName, 42) + "   ·   " + footerLabel;
			ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
					new Phrase(leftLabel, font(7.5f, Font.NORMAL, MUTED)), left, foot, 0);

			Phrase pageBit = new Phrase("Pág. " + writer.getPageNumber() + " de ", font(7.5f, Font.NORMAL, MUTED));
			float pageWidth = ColumnText.getWidth(pageBit);
			float pageX = right - 14f - pageWidth;
			ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, pageBit, pageX, foot, 0);
			cb.addTemplate(total, pageX + pageWidth, foot);
		}

		@Override
		public void onCloseDocument(PdfWriter writer, Document document) {
			ColumnText.showTextAligned(total, Element.ALIGN_LEFT,
					new Phrase(String.valueOf(writer.getPageNumber() - 1), font(7.5f, Font.NORMAL, MUTED)),
					0, 0, 0);
		}

		private static String trim(String value, int max) {
			if (value == null) {
				return "";
			}
			String clean = value.trim();
			return clean.length() <= max ? clean : clean.substring(0, max - 3) + "...";
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
	}
}
