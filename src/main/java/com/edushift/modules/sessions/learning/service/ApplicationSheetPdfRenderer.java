package com.edushift.modules.sessions.learning.service;

import com.edushift.modules.sessions.learning.entity.ApplicationSheet;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent.Activity;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent.Item;
import com.edushift.shared.branding.PlatformPdfBrand;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
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
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Renders one application sheet as a single-grade A4 PDF.
 */
@Component
public class ApplicationSheetPdfRenderer {

	private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.BOLD);
	private static final Font SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD);
	private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 11);
	private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 9);

	public byte[] render(ApplicationSheet sheet, String courseName, String schoolName) {
		String school = schoolName == null ? "" : schoolName;
		ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
		Document document = new Document(PageSize.A4, 36, 36, 40, 40);
		try {
			PdfWriter writer = PdfWriter.getInstance(document, out);
			writer.setCompressionLevel(0);
			PlatformPdfBrand.installFooter(document, writer);
			document.open();
			write(document, sheet, school, courseName);
		}
		catch (DocumentException ex) {
			throw new IllegalStateException("Error generating application sheet PDF", ex);
		}
		finally {
			document.close();
		}
		return out.toByteArray();
	}

	private void write(Document document, ApplicationSheet sheet, String school, String courseName)
			throws DocumentException {
		PlatformPdfBrand.writeHeader(document, school, null);

		String headline = sheet.getKind().name().equals("DIAGNOSTIC")
				? "Evaluación diagnóstica"
				: "Ficha de aprendizaje N.° " + sheet.getSheetNumber();
		Paragraph title = new Paragraph(headline, TITLE);
		title.setAlignment(Element.ALIGN_CENTER);
		title.setSpacingAfter(4);
		document.add(title);

		Paragraph subtitle = new Paragraph(sheet.getTitle(), SECTION);
		subtitle.setAlignment(Element.ALIGN_CENTER);
		subtitle.setSpacingAfter(10);
		document.add(subtitle);

		PdfPTable meta = new PdfPTable(2);
		meta.setWidthPercentage(100);
		meta.setWidths(new float[] { 28, 72 });
		meta.setSpacingAfter(10);
		addRow(meta, "Grado", sheet.getGrade());
		addRow(meta, "Curso", courseName == null ? "" : courseName);
		addRow(meta, "Nombre", "________________________________");
		addRow(meta, "Fecha", "____ / ____ / ________");
		document.add(meta);

		ApplicationSheetContent content = sheet.getContent();
		if (content == null) {
			return;
		}
		if (sheet.getKind().name().equals("DIAGNOSTIC")) {
			writeDiagnostic(document, content);
		}
		else {
			writeLearning(document, content);
		}
	}

	private void writeLearning(Document document, ApplicationSheetContent content) throws DocumentException {
		if (content.getWarmup() != null) {
			heading(document, "1. Me preparo para aprender");
			body(document, content.getWarmup().getPrompt());
			if (content.getWarmup().getOptions() != null) {
				body(document, String.join("    ", content.getWarmup().getOptions()));
			}
		}
		if (content.getPurpose() != null) {
			heading(document, "2. Qué vamos a lograr hoy");
			body(document, "Propósito: " + safe(content.getPurpose().getPurpose()));
			body(document, "Evidencia: " + safe(content.getPurpose().getEvidence()));
			body(document, "Criterio: " + safe(content.getPurpose().getCriterion()));
		}
		heading(document, "3. Desarrollamos las actividades");
		List<Activity> activities = content.getActivities();
		if (activities != null) {
			for (Activity activity : activities) {
				body(document, activity.getTitle()
						+ (activity.getMoment() == null || activity.getMoment().isBlank()
								? ""
								: " (" + activity.getMoment() + ")"));
				if (activity.getInstruction() != null && !activity.getInstruction().isBlank()) {
					body(document, activity.getInstruction());
				}
				writeItems(document, activity.getItems());
			}
		}
		if (content.getSelfAssessment() != null) {
			heading(document, "4. Evalúo mis pasos");
			body(document, content.getSelfAssessment().getPrompt());
			writeAssessmentTable(document, content.getSelfAssessment());
		}
		if (content.getReflection() != null) {
			heading(document, "5. Reflexiono sobre lo aprendido");
			writeItems(document, content.getReflection().getQuestions());
		}
		if (content.getClosingMessage() != null && !content.getClosingMessage().isBlank()) {
			body(document, content.getClosingMessage());
		}
	}

	private void writeDiagnostic(Document document, ApplicationSheetContent content) throws DocumentException {
		if (content.getSubtitle() != null && !content.getSubtitle().isBlank()) {
			body(document, content.getSubtitle());
		}
		heading(document, "Situación significativa");
		body(document, content.getSituation());
		if (content.getCompetencyMatrix() != null && !content.getCompetencyMatrix().isEmpty()) {
			heading(document, "Matriz de competencias");
			for (ApplicationSheetContent.CompetencyRow row : content.getCompetencyMatrix()) {
				body(document, row.getCompetency() + ": " + row.getCriterion());
			}
		}
		if (content.getTasks() != null) {
			heading(document, "Desarrollo");
			for (ApplicationSheetContent.DiagnosticTask task : content.getTasks()) {
				body(document, task.getTitle());
				if (task.getStimulus() != null && !task.getStimulus().isBlank()) {
					body(document, task.getStimulus());
				}
				writeItems(document, task.getItems());
			}
		}
		if (content.getAchievementLevels() != null && !content.getAchievementLevels().isEmpty()) {
			heading(document, "Niveles de logro");
			PdfPTable table = new PdfPTable(4);
			table.setWidthPercentage(100);
			headerCell(table, "Competencia");
			headerCell(table, "Logro");
			headerCell(table, "Proceso");
			headerCell(table, "Inicio");
			for (ApplicationSheetContent.AchievementLevel level : content.getAchievementLevels()) {
				cell(table, level.getCompetency());
				cell(table, level.getLogro());
				cell(table, level.getProceso());
				cell(table, level.getInicio());
			}
			document.add(table);
		}
		if (Boolean.TRUE.equals(content.getTeacherComment())) {
			heading(document, "Comentario del docente");
			body(document, "________________________________________________");
		}
	}

	private void writeItems(Document document, List<Item> items) throws DocumentException {
		if (items == null) {
			return;
		}
		for (Item item : items) {
			String kind = item.getKind() == null ? "LINE" : item.getKind();
			String line = switch (kind) {
				case "CHECKBOX" -> "[  ] " + item.getPrompt();
				case "CHOICE" -> item.getPrompt() + "  "
						+ (item.getChoices() == null ? "" : joinChoices(item.getChoices()));
				default -> item.getPrompt() + " ________________________________";
			};
			body(document, line);
		}
	}

	private static String joinChoices(List<String> choices) {
		StringBuilder builder = new StringBuilder();
		for (String choice : choices) {
			builder.append("( ) ").append(choice).append("  ");
		}
		return builder.toString().trim();
	}

	private void writeAssessmentTable(Document document,
			ApplicationSheetContent.SelfAssessment assessment) throws DocumentException {
		List<String> columns = assessment.getColumns();
		PdfPTable table = new PdfPTable(1 + columns.size());
		table.setWidthPercentage(100);
		table.setSpacingBefore(4);
		headerCell(table, "");
		for (String column : columns) {
			headerCell(table, column);
		}
		for (String row : assessment.getRows()) {
			cell(table, row);
			for (int i = 0; i < columns.size(); i++) {
				cell(table, "[  ]");
			}
		}
		document.add(table);
	}

	private static void heading(Document document, String text) throws DocumentException {
		Paragraph paragraph = new Paragraph(text, SECTION);
		paragraph.setSpacingBefore(8);
		paragraph.setSpacingAfter(4);
		document.add(paragraph);
	}

	private static void body(Document document, String text) throws DocumentException {
		Paragraph paragraph = new Paragraph(text == null ? "" : text, BODY);
		paragraph.setSpacingAfter(4);
		document.add(paragraph);
	}

	private static void addRow(PdfPTable table, String label, String value) {
		PdfPCell labelCell = new PdfPCell(new Phrase(label, SECTION));
		labelCell.setBorder(0);
		table.addCell(labelCell);
		PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "" : value, BODY));
		valueCell.setBorder(0);
		table.addCell(valueCell);
	}

	private static void headerCell(PdfPTable table, String text) {
		PdfPCell cell = new PdfPCell(new Phrase(text, SMALL));
		table.addCell(cell);
	}

	private static void cell(PdfPTable table, String text) {
		table.addCell(new PdfPCell(new Phrase(text == null ? "" : text, SMALL)));
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
