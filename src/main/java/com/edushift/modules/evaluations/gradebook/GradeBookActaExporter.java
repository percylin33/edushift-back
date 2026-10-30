package com.edushift.modules.evaluations.gradebook;

import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookCategoryEntry;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookCellEntry;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookEvaluationEntry;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookResponse;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookStudentEntry;
import com.edushift.shared.branding.PlatformPdfBrand;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Printable grade-register workbook and PDF.
 *
 * <p>Measurements follow the primaria acta: Arial Black 28 title and
 * period, Arial 20 teacher line, a three-row header (category banner,
 * session labels, vertical achievement and final average), Calibri 22
 * names and Arial 20 scores. Categories and columns still come from
 * the live grade book.</p>
 */
public final class GradeBookActaExporter {

	private static final float TITLE_ROW = 42.75f;
	private static final float PERIOD_ROW = 72.75f;
	private static final float TEACHER_ROW = 36f;
	private static final float HEADER_TOP = 15f;
	private static final float HEADER_MID = 18f;
	private static final float HEADER_BOTTOM = 80.25f;
	private static final float STUDENT_ROW = 37.5f;
	private static final int NAME_WIDTH = (int) Math.round(88.71 * 256);
	private static final int MARK_WIDTH = (int) Math.round(7.71 * 256);
	private static final int TITLE_ROW_INDEX = 2;
	private static final int PERIOD_ROW_INDEX = 3;
	private static final int TEACHER_ROW_INDEX = 5;
	private static final int HEADER_TOP_INDEX = 7;
	private static final int HEADER_BOTTOM_INDEX = 9;
	private static final int FIRST_STUDENT_INDEX = 10;

	private static final XSSFColor WHITE = rgb(255, 255, 255);
	private static final XSSFColor ACHIEVEMENT = rgb(0xE2, 0xEF, 0xDA);
	private static final XSSFColor SUMMARY_HEADER = rgb(0xBF, 0xBF, 0xBF);
	private static final XSSFColor SUMMARY_CELL = rgb(0xD9, 0xD9, 0xD9);
	private static final Color PDF_ACHIEVEMENT = new Color(0xE2, 0xEF, 0xDA);
	private static final Color PDF_SUMMARY = new Color(0xD9, 0xD9, 0xD9);
	private static final Color PDF_SUMMARY_HEADER = new Color(0xBF, 0xBF, 0xBF);

	private final Map<String, XSSFFont> fonts = new HashMap<>();
	private final Map<String, XSSFCellStyle> styles = new HashMap<>();
	private short numberFormat;

	private GradeBookActaExporter() {
	}

	public static byte[] toXlsx(GradeBookResponse gradeBook, String academicYearName) {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			new GradeBookActaExporter().writeXlsx(workbook, gradeBook, academicYearName);
			workbook.write(out);
			return out.toByteArray();
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to export gradebook XLSX", ex);
		}
	}

	public static byte[] toPdf(GradeBookResponse gradeBook, String academicYearName, byte[] schoolLogo) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Document doc = new Document(PageSize.A4.rotate(), 16, 16, 16, 36);
			PdfWriter writer = PdfWriter.getInstance(doc, out);
			PlatformPdfBrand.installFooter(doc, writer);
			doc.open();
			writePdf(doc, gradeBook, academicYearName, schoolLogo);
			doc.close();
			return out.toByteArray();
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to export gradebook PDF", ex);
		}
	}

	private void writeXlsx(XSSFWorkbook workbook, GradeBookResponse gb, String academicYearName) {
		numberFormat = workbook.createDataFormat().getFormat("0.##");
		List<Col> columns = columnsOf(gb);
		int lastCol = 2 + columns.size();

		XSSFSheet sheet = workbook.createSheet(sheetName(gb));
		sheet.setDisplayGridlines(false);
		sheet.createRow(0).setHeightInPoints(15.75f);
		sheet.createRow(1);
		row(sheet, TITLE_ROW_INDEX, TITLE_ROW);
		row(sheet, PERIOD_ROW_INDEX, PERIOD_ROW);
		row(sheet, 4, 18.75f);
		row(sheet, TEACHER_ROW_INDEX, TEACHER_ROW);
		row(sheet, 6, 18f);
		row(sheet, HEADER_TOP_INDEX, HEADER_TOP);
		row(sheet, HEADER_TOP_INDEX + 1, HEADER_MID);
		row(sheet, HEADER_BOTTOM_INDEX, HEADER_BOTTOM);

		paint(workbook, sheet, TITLE_ROW_INDEX, TITLE_ROW_INDEX, 0, lastCol, title(academicYearName),
				font(workbook, "Arial Black", 28, true),
				HorizontalAlignment.CENTER, false, (short) 0, null,
				false, false, true, true, BorderStyle.MEDIUM);
		paint(workbook, sheet, PERIOD_ROW_INDEX, PERIOD_ROW_INDEX, 0, lastCol, periodHeadline(gb),
				font(workbook, "Arial Black", 28, true),
				HorizontalAlignment.CENTER, false, (short) 0, null,
				false, false, true, true, BorderStyle.MEDIUM);
		paint(workbook, sheet, TEACHER_ROW_INDEX, TEACHER_ROW_INDEX, 0, lastCol, teacherLine(gb),
				font(workbook, "Arial", 20, true),
				HorizontalAlignment.LEFT, false, (short) 0, null,
				true, true, true, true, BorderStyle.MEDIUM);

		paint(workbook, sheet, HEADER_TOP_INDEX, HEADER_BOTTOM_INDEX, 0, 0, "N°",
				font(workbook, "Arial", 18, true),
				HorizontalAlignment.CENTER, true, (short) 0, WHITE,
				true, true, true, true, BorderStyle.THIN);
		paint(workbook, sheet, HEADER_TOP_INDEX, HEADER_BOTTOM_INDEX, 1, 1, "APELLIDOS Y NOMBRES",
				font(workbook, "Arial", 22, true),
				HorizontalAlignment.CENTER, true, (short) 0, WHITE,
				true, true, true, true, BorderStyle.THIN);

		for (Col col : columns) {
			if (col.kind == Kind.ACHIEVEMENT) {
				paint(workbook, sheet, HEADER_TOP_INDEX, HEADER_BOTTOM_INDEX, col.index, col.index,
						"NIVEL DEL LOGRO",
						font(workbook, "Arial Black", 12, true),
						HorizontalAlignment.CENTER, true, (short) 90, ACHIEVEMENT,
						true, true, true, true, BorderStyle.THIN);
			} else if (col.grouped && col.bannerSpan > 0) {
				paint(workbook, sheet, HEADER_TOP_INDEX, HEADER_TOP_INDEX + 1,
						col.index, col.index + col.bannerSpan - 1,
						upper(col.category.name()),
						font(workbook, "Arial Black", 22, true),
						HorizontalAlignment.CENTER, true, (short) 0, WHITE,
						true, true, true, true, BorderStyle.THIN);
			}
			if (col.kind == Kind.EVAL && col.grouped) {
				paint(workbook, sheet, HEADER_BOTTOM_INDEX, HEADER_BOTTOM_INDEX, col.index, col.index,
						upper(col.evaluation.name()),
						font(workbook, "Arial Black", 18, true),
						HorizontalAlignment.CENTER, true, (short) 0, WHITE,
						true, true, true, true, BorderStyle.THIN);
			} else if (col.kind == Kind.EVAL) {
				String label = col.category != null ? col.category.name() : col.evaluation.name();
				paint(workbook, sheet, HEADER_TOP_INDEX, HEADER_BOTTOM_INDEX, col.index, col.index,
						upper(label),
						font(workbook, "Arial Black", 12, true),
						HorizontalAlignment.CENTER, true, (short) 90, WHITE,
						true, true, true, true, BorderStyle.THIN);
			}
		}
		paint(workbook, sheet, HEADER_TOP_INDEX, HEADER_BOTTOM_INDEX, lastCol, lastCol, "PROMEDIO GENERAL",
				font(workbook, "Arial Black", 12, true),
				HorizontalAlignment.CENTER, true, (short) 90, SUMMARY_HEADER,
				true, true, true, true, BorderStyle.THIN);

		sheet.setColumnWidth(0, MARK_WIDTH);
		sheet.setColumnWidth(1, NAME_WIDTH);
		for (int c = 2; c <= lastCol; c++) {
			sheet.setColumnWidth(c, MARK_WIDTH);
		}

		Map<String, GradeBookCellEntry> cells = indexCells(gb.cells());
		XSSFFont numberFont = font(workbook, "Calibri", 22, true);
		XSSFFont markFont = font(workbook, "Arial", 20, true);
		int excelRow = FIRST_STUDENT_INDEX;
		int n = 1;
		List<GradeBookStudentEntry> students = gb.students() == null ? List.of() : gb.students();
		for (GradeBookStudentEntry student : students) {
			row(sheet, excelRow, STUDENT_ROW);
			putText(workbook, sheet, excelRow, 0, String.valueOf(n++), numberFont,
					HorizontalAlignment.CENTER, WHITE);
			putText(workbook, sheet, excelRow, 1, studentLabel(student), numberFont,
					HorizontalAlignment.LEFT, WHITE);
			for (Col col : columns) {
				if (col.kind == Kind.ACHIEVEMENT) {
					putMark(workbook, sheet, excelRow, col.index,
							achievement(student, col.category.publicUuid()), markFont, ACHIEVEMENT);
				} else {
					GradeBookCellEntry cell = cells.get(
							student.publicUuid() + "::" + col.evaluation.publicUuid());
					putMark(workbook, sheet, excelRow, col.index, cellValue(cell), markFont, WHITE);
				}
			}
			putMark(workbook, sheet, excelRow, lastCol, generalAverage(student), markFont, SUMMARY_CELL);
			excelRow++;
		}

		int lastRow = Math.max(HEADER_BOTTOM_INDEX, excelRow - 1);
		sheet.setRepeatingRows(new CellRangeAddress(HEADER_TOP_INDEX, HEADER_BOTTOM_INDEX, 0, lastCol));
		PrintSetup print = sheet.getPrintSetup();
		print.setLandscape(true);
		print.setPaperSize(PrintSetup.A4_PAPERSIZE);
		print.setFitWidth((short) 1);
		print.setFitHeight((short) 0);
		sheet.setFitToPage(true);
		sheet.setMargin(XSSFSheet.LeftMargin, 0.4);
		sheet.setMargin(XSSFSheet.RightMargin, 0.4);
		sheet.setMargin(XSSFSheet.TopMargin, 0.4);
		sheet.setMargin(XSSFSheet.BottomMargin, 0.5);
		workbook.setPrintArea(0, 0, lastCol, TITLE_ROW_INDEX, lastRow);
	}

	private static void writePdf(
			Document doc,
			GradeBookResponse gb,
			String academicYearName,
			byte[] schoolLogo
	) throws Exception {
		PlatformPdfBrand.writeHeader(doc, title(academicYearName), schoolLogo);
		com.lowagie.text.Font periodFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
		com.lowagie.text.Font metaFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
		Paragraph period = new Paragraph(periodHeadline(gb), periodFont);
		period.setAlignment(Element.ALIGN_CENTER);
		period.setSpacingAfter(6f);
		doc.add(period);
		Paragraph meta = new Paragraph(teacherLine(gb), metaFont);
		meta.setSpacingAfter(8f);
		doc.add(meta);

		List<Col> columns = columnsOf(gb);
		int lastCol = 2 + columns.size();
		PdfPTable table = new PdfPTable(lastCol + 1);
		table.setWidthPercentage(100);
		table.setHeaderRows(2);
		float[] widths = new float[lastCol + 1];
		widths[0] = 4f;
		widths[1] = 22f;
		for (int i = 2; i < widths.length; i++) {
			widths[i] = 5f;
		}
		table.setWidths(widths);

		com.lowagie.text.Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
		com.lowagie.text.Font body = FontFactory.getFont(FontFactory.HELVETICA, 8);
		com.lowagie.text.Font bodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);

		pdfCell(table, "N°", head, 1, 2, Element.ALIGN_CENTER, Color.WHITE, false);
		pdfCell(table, "APELLIDOS Y NOMBRES", head, 1, 2, Element.ALIGN_CENTER, Color.WHITE, false);
		for (Col col : columns) {
			if (col.kind == Kind.ACHIEVEMENT) {
				pdfCell(table, "NIVEL DEL LOGRO", head, 1, 2, Element.ALIGN_CENTER, PDF_ACHIEVEMENT, true);
			} else if (col.grouped && col.bannerSpan > 0) {
				pdfCell(table, upper(col.category.name()), head, col.bannerSpan, 1,
						Element.ALIGN_CENTER, Color.WHITE, false);
			} else if (col.kind == Kind.EVAL && !col.grouped) {
				String label = col.category != null ? col.category.name() : col.evaluation.name();
				pdfCell(table, upper(label), head, 1, 2, Element.ALIGN_CENTER, Color.WHITE, true);
			}
		}
		pdfCell(table, "PROMEDIO GENERAL", head, 1, 2, Element.ALIGN_CENTER, PDF_SUMMARY_HEADER, true);
		for (Col col : columns) {
			if (col.kind == Kind.EVAL && col.grouped) {
				pdfCell(table, upper(col.evaluation.name()), head, 1, 1,
						Element.ALIGN_CENTER, Color.WHITE, false);
			}
		}

		Map<String, GradeBookCellEntry> cells = indexCells(gb.cells());
		int n = 1;
		List<GradeBookStudentEntry> students = gb.students() == null ? List.of() : gb.students();
		for (GradeBookStudentEntry student : students) {
			pdfCell(table, String.valueOf(n++), bodyBold, 1, 1, Element.ALIGN_CENTER, Color.WHITE, false);
			pdfCell(table, studentLabel(student), bodyBold, 1, 1, Element.ALIGN_LEFT, Color.WHITE, false);
			for (Col col : columns) {
				if (col.kind == Kind.ACHIEVEMENT) {
					pdfCell(table, textOf(achievement(student, col.category.publicUuid())), body, 1, 1,
							Element.ALIGN_CENTER, PDF_ACHIEVEMENT, false);
				} else {
					GradeBookCellEntry cell = cells.get(
							student.publicUuid() + "::" + col.evaluation.publicUuid());
					pdfCell(table, textOf(cellValue(cell)), body, 1, 1,
							Element.ALIGN_CENTER, Color.WHITE, false);
				}
			}
			pdfCell(table, textOf(generalAverage(student)), bodyBold, 1, 1,
					Element.ALIGN_CENTER, PDF_SUMMARY, false);
		}
		doc.add(table);
	}

	private static void pdfCell(
			PdfPTable table,
			String text,
			com.lowagie.text.Font font,
			int colspan,
			int rowspan,
			int align,
			Color fill,
			boolean vertical
	) {
		PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
		cell.setColspan(colspan);
		cell.setRowspan(rowspan);
		cell.setHorizontalAlignment(align);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setBackgroundColor(fill);
		cell.setPadding(3f);
		cell.setBorder(Rectangle.BOX);
		cell.setBorderWidth(0.6f);
		if (vertical) {
			cell.setRotation(90);
			cell.setMinimumHeight(46f);
		} else {
			cell.setMinimumHeight(16f);
		}
		table.addCell(cell);
	}

	private void putText(
			XSSFWorkbook workbook,
			XSSFSheet sheet,
			int rowIndex,
			int col,
			String value,
			XSSFFont font,
			HorizontalAlignment align,
			XSSFColor fill
	) {
		Cell cell = row(sheet, rowIndex, STUDENT_ROW).createCell(col);
		cell.setCellValue(value == null ? "" : value);
		cell.setCellStyle(style(workbook, font, align, false, (short) 0, fill,
				true, true, true, true, BorderStyle.THIN, false));
	}

	private void putMark(
			XSSFWorkbook workbook,
			XSSFSheet sheet,
			int rowIndex,
			int col,
			Mark mark,
			XSSFFont font,
			XSSFColor fill
	) {
		Cell cell = row(sheet, rowIndex, STUDENT_ROW).createCell(col);
		boolean numeric = mark.number != null;
		cell.setCellStyle(style(workbook, font, HorizontalAlignment.CENTER, false, (short) 0, fill,
				true, true, true, true, BorderStyle.THIN, numeric));
		if (numeric) {
			cell.setCellValue(mark.number.doubleValue());
		} else {
			cell.setCellValue(mark.text == null ? "" : mark.text);
		}
	}

	private void paint(
			XSSFWorkbook workbook,
			XSSFSheet sheet,
			int r1,
			int r2,
			int c1,
			int c2,
			String value,
			XSSFFont font,
			HorizontalAlignment align,
			boolean wrap,
			short rotation,
			XSSFColor fill,
			boolean top,
			boolean bottom,
			boolean left,
			boolean right,
			BorderStyle weight
	) {
		for (int r = r1; r <= r2; r++) {
			Row excelRow = row(sheet, r, -1);
			for (int c = c1; c <= c2; c++) {
				Cell cell = excelRow.getCell(c);
				if (cell == null) {
					cell = excelRow.createCell(c);
				}
				if (r == r1 && c == c1 && value != null) {
					cell.setCellValue(value);
				}
				cell.setCellStyle(style(workbook, font, align, wrap, rotation, fill,
						top && r == r1, bottom && r == r2, left && c == c1, right && c == c2,
						weight, false));
			}
		}
		if (r1 != r2 || c1 != c2) {
			sheet.addMergedRegion(new CellRangeAddress(r1, r2, c1, c2));
		}
	}

	private XSSFCellStyle style(
			XSSFWorkbook workbook,
			XSSFFont font,
			HorizontalAlignment align,
			boolean wrap,
			short rotation,
			XSSFColor fill,
			boolean top,
			boolean bottom,
			boolean left,
			boolean right,
			BorderStyle weight,
			boolean numeric
	) {
		String key = font.getFontName() + "|" + font.getFontHeightInPoints() + "|" + font.getBold()
				+ "|" + align + "|" + wrap + "|" + rotation
				+ "|" + (fill == null ? "-" : fill.getARGBHex())
				+ "|" + top + bottom + left + right + "|" + weight + "|" + numeric;
		return styles.computeIfAbsent(key, ignored -> {
			XSSFCellStyle cellStyle = workbook.createCellStyle();
			cellStyle.setFont(font);
			cellStyle.setAlignment(align);
			cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			cellStyle.setWrapText(wrap);
			cellStyle.setRotation(rotation);
			if (align == HorizontalAlignment.LEFT) {
				cellStyle.setIndention((short) 1);
			}
			if (fill != null) {
				cellStyle.setFillForegroundColor(fill);
				cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			}
			cellStyle.setBorderTop(top ? weight : BorderStyle.NONE);
			cellStyle.setBorderBottom(bottom ? weight : BorderStyle.NONE);
			cellStyle.setBorderLeft(left ? weight : BorderStyle.NONE);
			cellStyle.setBorderRight(right ? weight : BorderStyle.NONE);
			if (numeric) {
				cellStyle.setDataFormat(numberFormat);
			}
			return cellStyle;
		});
	}

	private XSSFFont font(XSSFWorkbook workbook, String name, int size, boolean bold) {
		String key = name + "|" + size + "|" + bold;
		return fonts.computeIfAbsent(key, ignored -> {
			XSSFFont created = workbook.createFont();
			created.setFontName(name);
			created.setFontHeightInPoints((short) size);
			created.setBold(bold);
			return created;
		});
	}

	private static List<Col> columnsOf(GradeBookResponse gb) {
		List<GradeBookCategoryEntry> categories = gb.categories() == null
				? List.of()
				: gb.categories().stream()
						.sorted(Comparator.comparing(
								c -> c.sortOrder() == null ? Integer.MAX_VALUE : c.sortOrder()))
						.toList();
		Map<UUID, GradeBookCategoryEntry> byId = new LinkedHashMap<>();
		for (GradeBookCategoryEntry category : categories) {
			byId.put(category.publicUuid(), category);
		}
		Map<UUID, List<GradeBookEvaluationEntry>> grouped = new LinkedHashMap<>();
		List<GradeBookEvaluationEntry> loose = new ArrayList<>();
		List<GradeBookEvaluationEntry> evaluations = gb.evaluations() == null ? List.of() : gb.evaluations();
		for (GradeBookEvaluationEntry evaluation : evaluations) {
			UUID categoryId = evaluation.categoryPublicUuid();
			if (categoryId != null && byId.containsKey(categoryId)) {
				grouped.computeIfAbsent(categoryId, ignored -> new ArrayList<>()).add(evaluation);
			} else {
				loose.add(evaluation);
			}
		}

		List<Col> columns = new ArrayList<>();
		int index = 2;
		for (GradeBookCategoryEntry category : categories) {
			List<GradeBookEvaluationEntry> evals = grouped.getOrDefault(category.publicUuid(), List.of());
			if (evals.isEmpty()) {
				continue;
			}
			boolean multi = evals.size() > 1;
			for (int i = 0; i < evals.size(); i++) {
				columns.add(new Col(Kind.EVAL, category, evals.get(i), index++, multi, multi && i == 0 ? evals.size() : 0));
			}
			if (multi && category.contributesToAverage()) {
				columns.add(new Col(Kind.ACHIEVEMENT, category, null, index++, false, 0));
			}
		}
		for (GradeBookEvaluationEntry evaluation : loose) {
			columns.add(new Col(Kind.EVAL, null, evaluation, index++, false, 0));
		}
		return columns;
	}

	private static Map<String, GradeBookCellEntry> indexCells(List<GradeBookCellEntry> cells) {
		Map<String, GradeBookCellEntry> map = new HashMap<>();
		if (cells == null) {
			return map;
		}
		for (GradeBookCellEntry cell : cells) {
			map.put(cell.studentPublicUuid() + "::" + cell.evaluationPublicUuid(), cell);
		}
		return map;
	}

	private static String title(String academicYearName) {
		if (academicYearName == null || academicYearName.isBlank()) {
			return "REGISTRO DE NOTAS";
		}
		return "REGISTRO DE NOTAS - " + academicYearName.trim();
	}

	private static String periodHeadline(GradeBookResponse gb) {
		if (gb.periodType() == PeriodType.ANUAL) {
			return "ANUAL";
		}
		if (gb.periodOrdinal() != null && gb.periodType() != null) {
			return gb.periodOrdinal() + "° " + gb.periodType().displayLabel().toUpperCase(Locale.ROOT);
		}
		return gb.periodName() == null ? "" : gb.periodName().toUpperCase(Locale.ROOT);
	}

	private static String teacherLine(GradeBookResponse gb) {
		return "DOCENTE: " + blank(gb.teacherFullName())
				+ "      CURSO: " + blank(gb.courseName())
				+ "      SALÓN: " + salon(gb);
	}

	private static String salon(GradeBookResponse gb) {
		if (gb.gradeName() != null && !gb.gradeName().isBlank()) {
			return gb.gradeName() + " — " + blank(gb.sectionName());
		}
		return blank(gb.sectionName());
	}

	private static String sheetName(GradeBookResponse gb) {
		String raw = salon(gb);
		if (raw.isBlank()) {
			raw = "Registro";
		}
		String cleaned = raw.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
		if (cleaned.length() > 31) {
			cleaned = cleaned.substring(0, 31);
		}
		return cleaned.isBlank() ? "Registro" : cleaned;
	}

	private static String studentLabel(GradeBookStudentEntry student) {
		String name = blank(student.fullName());
		String status = student.enrollmentStatus();
		if ("WITHDRAWN".equals(status)) {
			return name + " (Ret.)";
		}
		if ("TRANSFERRED".equals(status)) {
			return name + " (Trasl.)";
		}
		if ("GRADUATED".equals(status)) {
			return name + " (Egr.)";
		}
		return name;
	}

	private static Mark achievement(GradeBookStudentEntry student, UUID categoryId) {
		if (student.categoryLiteralAverages() != null) {
			String literal = student.categoryLiteralAverages().get(categoryId);
			if (literal != null && !literal.isBlank()) {
				return Mark.text(literal);
			}
		}
		if (student.categoryAverages() != null && student.categoryAverages().get(categoryId) != null) {
			return Mark.number(student.categoryAverages().get(categoryId));
		}
		return Mark.text("");
	}

	private static Mark generalAverage(GradeBookStudentEntry student) {
		if (student.literalAverage() != null && !student.literalAverage().isBlank()) {
			return Mark.text(student.literalAverage());
		}
		if (student.weightedAverage() != null) {
			return Mark.number(student.weightedAverage());
		}
		return Mark.text("");
	}

	private static Mark cellValue(GradeBookCellEntry cell) {
		if (cell == null) {
			return Mark.text("");
		}
		if (cell.score() != null) {
			return Mark.number(cell.score());
		}
		return Mark.text(cell.literal() == null ? "" : cell.literal());
	}

	private static String textOf(Mark mark) {
		if (mark.number != null) {
			return mark.number.stripTrailingZeros().toPlainString();
		}
		return mark.text == null ? "" : mark.text;
	}

	private static String upper(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.trim().toUpperCase(Locale.forLanguageTag("es"));
	}

	private static String blank(String value) {
		return value == null ? "" : value;
	}

	private static Row row(XSSFSheet sheet, int index, float height) {
		Row excelRow = sheet.getRow(index);
		if (excelRow == null) {
			excelRow = sheet.createRow(index);
		}
		if (height > 0) {
			excelRow.setHeightInPoints(height);
		}
		return excelRow;
	}

	private static XSSFColor rgb(int red, int green, int blue) {
		return new XSSFColor(new Color(red, green, blue), null);
	}

	private enum Kind {
		EVAL,
		ACHIEVEMENT
	}

	private static final class Col {
		private final Kind kind;
		private final GradeBookCategoryEntry category;
		private final GradeBookEvaluationEntry evaluation;
		private final int index;
		private final boolean grouped;
		private final int bannerSpan;

		private Col(
				Kind kind,
				GradeBookCategoryEntry category,
				GradeBookEvaluationEntry evaluation,
				int index,
				boolean grouped,
				int bannerSpan
		) {
			this.kind = kind;
			this.category = category;
			this.evaluation = evaluation;
			this.index = index;
			this.grouped = grouped;
			this.bannerSpan = bannerSpan;
		}
	}

	private static final class Mark {
		private final BigDecimal number;
		private final String text;

		private Mark(BigDecimal number, String text) {
			this.number = number;
			this.text = text;
		}

		private static Mark number(BigDecimal number) {
			return new Mark(number, null);
		}

		private static Mark text(String text) {
			return new Mark(null, text);
		}
	}
}
