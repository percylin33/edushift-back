package com.edushift.modules.teachers.bulk;

import com.edushift.modules.students.entity.DocumentType;
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
 * Downloadable {@code .xlsx} template for teacher bulk import with
 * Spanish headers (compatible with {@link TeacherBulkImportParser}).
 */
@Component
public class TeacherTemplateGenerator {

	private static final String SHEET_DATA = "Docentes";
	private static final String SHEET_REFERENCE = "Referencia";

	/** Canonical key → Spanish header. */
	private static final Map<String, String> COLUMNS =
			new LinkedHashMap<>(TeacherBulkImportHeaderAliases.templateColumnsEs());

	public byte[] generate() {
		try (Workbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			writeDataSheet(workbook);
			writeReferenceSheet(workbook);
			workbook.write(out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not generate teachers template", e);
		}
	}

	private void writeDataSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet(SHEET_DATA);
		CellStyle headerStyle = headerStyle(workbook);

		Row header = sheet.createRow(0);
		int col = 0;
		for (String label : COLUMNS.values()) {
			Cell cell = header.createCell(col++);
			cell.setCellValue(label);
			cell.setCellStyle(headerStyle);
		}

		Row sample = sheet.createRow(1);
		sample.createCell(0).setCellValue("DNI");
		sample.createCell(1).setCellValue("45678912");
		sample.createCell(2).setCellValue("María");
		sample.createCell(3).setCellValue("García");
		sample.createCell(4).setCellValue("López");
		sample.createCell(5).setCellValue("15/04/1988");
		sample.createCell(6).setCellValue("FEMENINO");
		sample.createCell(7).setCellValue("maria.garcia@colegio.edu.pe");
		sample.createCell(8).setCellValue("+51 999888777");
		sample.createCell(9).setCellValue("Lic.");
		sample.createCell(10).setCellValue("Matemática, Física");
		sample.createCell(11).setCellValue("01/03/2024");
		sample.createCell(12).setCellValue("ACTIVO");

		for (int i = 0; i < COLUMNS.size(); i++) {
			sheet.setColumnWidth(i, 22 * 256);
		}
		sheet.createFreezePane(0, 1);
	}

	private void writeReferenceSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet(SHEET_REFERENCE);
		CellStyle headerStyle = headerStyle(workbook);

		writeReferenceColumn(sheet, headerStyle, 0, "Tipo de documento",
				List.of(DocumentType.values()).stream().map(Enum::name).toList());
		writeReferenceColumn(sheet, headerStyle, 1, "Género",
				List.of("MASCULINO", "FEMENINO", "OTRO", "NO_ESPECIFICADO"));
		writeReferenceColumn(sheet, headerStyle, 2, "Estado laboral",
				List.of("ACTIVO", "LICENCIA", "RENUNCIADO", "JUBILADO", "SUSPENDIDO"));

		for (int i = 0; i < 3; i++) {
			sheet.setColumnWidth(i, 28 * 256);
		}

		int notesCol = 4;
		sheet.setColumnWidth(notesCol, 72 * 256);
		writeNote(sheet, headerStyle, notesCol, 0, "Cómo llenar la plantilla");
		writeNote(sheet, null, notesCol, 1,
				"1. Edita la hoja Docentes — no borres ni renombres la fila de encabezados.");
		writeNote(sheet, null, notesCol, 2,
				"2. Obligatorios: Tipo de documento, Número de documento, Nombres, Apellido paterno.");
		writeNote(sheet, null, notesCol, 3,
				"3. Fechas en formato día/mes/año (dd/MM/yyyy), p. ej. 01/03/2024.");
		writeNote(sheet, null, notesCol, 4,
				"4. Especialidades: lista separada por comas (ej. Matemática, Física).");
		writeNote(sheet, null, notesCol, 5,
				"5. Estado laboral por defecto es ACTIVO si lo dejas vacío.");
		writeNote(sheet, null, notesCol, 6,
				"6. Género: MASCULINO, FEMENINO, OTRO o NO_ESPECIFICADO.");
	}

	private void writeReferenceColumn(Sheet sheet, CellStyle headerStyle,
			int columnIndex, String name, List<String> values) {
		Row header = sheet.getRow(0);
		if (header == null) {
			header = sheet.createRow(0);
		}
		Cell h = header.createCell(columnIndex);
		h.setCellValue(name);
		h.setCellStyle(headerStyle);

		for (int i = 0; i < values.size(); i++) {
			Row r = sheet.getRow(i + 1);
			if (r == null) {
				r = sheet.createRow(i + 1);
			}
			r.createCell(columnIndex).setCellValue(values.get(i));
		}
	}

	private void writeNote(Sheet sheet, CellStyle style, int col, int rowIdx, String text) {
		Row row = sheet.getRow(rowIdx);
		if (row == null) {
			row = sheet.createRow(rowIdx);
		}
		Cell c = row.createCell(col);
		c.setCellValue(text);
		if (style != null) {
			c.setCellStyle(style);
		}
	}

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

	/** Exposed for tests. */
	public static Map<String, String> columns() {
		return Map.copyOf(COLUMNS);
	}
}
