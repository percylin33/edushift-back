package com.edushift.modules.students.service.bulk;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Generates the downloadable {@code .xlsx} template for the student
 * bulk-import flow.
 *
 * <p>The template ships with:
 * <ul>
 *   <li>A sheet "Students" with the canonical header row and a single
 *       sample row admins can edit or delete.</li>
 *   <li>A sheet "Reference" listing the allowed enum values
 *       ({@code DocumentType}, {@code Gender},
 *       {@code EnrollmentStatus}) so admins don't have to consult
 *       documentation.</li>
 * </ul>
 *
 * <p>We deliberately do <em>not</em> wire data validation drop-downs on
 * the cells: that requires {@code DataValidation} support which
 * complicates streaming and varies between Excel and Google Sheets. The
 * server-side parser is the source of truth.
 */
@Component
public class StudentTemplateGenerator {

	private static final String SHEET_DATA = "Students";
	private static final String SHEET_REFERENCE = "Reference";

	/**
	 * Order matters — this is the order the columns appear in the
	 * generated template. The keys are the canonical (lower-cased)
	 * names the parser recognises; the value is what the user sees
	 * in the header row.
	 */
	private static final Map<String, String> COLUMNS = buildColumns();

	private static Map<String, String> buildColumns() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("documentType", "documentType");
		m.put("documentNumber", "documentNumber");
		m.put("firstName", "firstName");
		m.put("lastName", "lastName");
		m.put("secondLastName", "secondLastName");
		m.put("birthDate", "birthDate");
		m.put("gender", "gender");
		m.put("email", "email");
		m.put("phone", "phone");
		m.put("address", "address");
		m.put("enrollmentStatus", "enrollmentStatus");
		m.put("enrollmentDate", "enrollmentDate");
		return m;
	}

	public byte[] generate() {
		try (Workbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			writeDataSheet(workbook);
			writeReferenceSheet(workbook);
			workbook.write(out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException(
					"Could not generate students template", e);
		}
	}

	// ---------------------------------------------------------------------------
	// Data sheet
	// ---------------------------------------------------------------------------

	private void writeDataSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet(SHEET_DATA);
		CellStyle headerStyle = headerStyle(workbook);

		Row header = sheet.createRow(0);
		int col = 0;
		for (String label : COLUMNS.values()) {
			Cell cell = header.createCell(col);
			cell.setCellValue(label);
			cell.setCellStyle(headerStyle);
			col++;
		}
		// Sample row — admins can replace or delete.
		Row sample = sheet.createRow(1);
		sample.createCell(0).setCellValue("DNI");
		sample.createCell(1).setCellValue("12345678");
		sample.createCell(2).setCellValue("Ada");
		sample.createCell(3).setCellValue("Lovelace");
		sample.createCell(4).setCellValue("Byron");
		sample.createCell(5).setCellValue("1815-12-10");
		sample.createCell(6).setCellValue("FEMALE");
		sample.createCell(7).setCellValue("ada@example.com");
		sample.createCell(8).setCellValue("");
		sample.createCell(9).setCellValue("");
		sample.createCell(10).setCellValue("ENROLLED");
		sample.createCell(11).setCellValue("2026-03-01");

		for (int i = 0; i < COLUMNS.size(); i++) {
			sheet.setColumnWidth(i, 18 * 256);
		}
		sheet.createFreezePane(0, 1);
	}

	// ---------------------------------------------------------------------------
	// Reference sheet
	// ---------------------------------------------------------------------------

	private void writeReferenceSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet(SHEET_REFERENCE);
		CellStyle headerStyle = headerStyle(workbook);

		writeReferenceColumn(sheet, headerStyle, 0, "documentType",
				List.of(DocumentType.values()).stream().map(Enum::name).toList());
		writeReferenceColumn(sheet, headerStyle, 1, "gender",
				List.of(Gender.values()).stream().map(Enum::name).toList());
		writeReferenceColumn(sheet, headerStyle, 2, "enrollmentStatus",
				List.of(EnrollmentStatus.values()).stream().map(Enum::name).toList());

		sheet.setColumnWidth(0, 22 * 256);
		sheet.setColumnWidth(1, 22 * 256);
		sheet.setColumnWidth(2, 22 * 256);

		// A small "How to use" block on the right.
		int notesCol = 4;
		sheet.setColumnWidth(notesCol, 70 * 256);
		writeNote(sheet, headerStyle, notesCol, 0, "How to fill the template");
		writeNote(sheet, null, notesCol, 1,
				"1. Edit the Students sheet — keep the header row intact.");
		writeNote(sheet, null, notesCol, 2,
				"2. Required columns: documentType, documentNumber, firstName, lastName.");
		writeNote(sheet, null, notesCol, 3,
				"3. Dates use ISO format (yyyy-MM-dd), e.g. 2026-03-01.");
		writeNote(sheet, null, notesCol, 4,
				"4. Enum values must match this Reference sheet exactly (uppercase).");
		writeNote(sheet, null, notesCol, 5,
				"5. Empty rows are skipped; rows with errors are reported "
						+ "individually after upload.");
	}

	private void writeReferenceColumn(Sheet sheet, CellStyle headerStyle,
			int columnIndex, String name, List<String> values) {
		Row header = sheet.getRow(0);
		if (header == null) header = sheet.createRow(0);
		Cell h = header.createCell(columnIndex);
		h.setCellValue(name);
		h.setCellStyle(headerStyle);

		for (int i = 0; i < values.size(); i++) {
			Row r = sheet.getRow(i + 1);
			if (r == null) r = sheet.createRow(i + 1);
			r.createCell(columnIndex).setCellValue(values.get(i));
		}
	}

	private void writeNote(Sheet sheet, CellStyle style, int col, int rowIdx, String text) {
		Row row = sheet.getRow(rowIdx);
		if (row == null) row = sheet.createRow(rowIdx);
		Cell c = row.createCell(col);
		c.setCellValue(text);
		if (style != null) c.setCellStyle(style);
	}

	// ---------------------------------------------------------------------------
	// Styling
	// ---------------------------------------------------------------------------

	private CellStyle headerStyle(Workbook workbook) {
		Font font = workbook.createFont();
		font.setBold(true);
		font.setColor(IndexedColors.WHITE.getIndex());

		CellStyle style = workbook.createCellStyle();
		style.setFont(font);
		style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		style.setBorderBottom(BorderStyle.THIN);
		return style;
	}
}
