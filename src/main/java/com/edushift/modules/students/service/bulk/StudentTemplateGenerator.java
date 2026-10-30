package com.edushift.modules.students.service.bulk;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.RelationshipType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
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
 * Generates the Spanish downloadable {@code .xlsx} template for student
 * bulk import (headers + sample + Reference sheet).
 */
@Component
public class StudentTemplateGenerator {

	private static final String SHEET_DATA = "Estudiantes";
	private static final String SHEET_REFERENCE = "Referencia";

	private static final Map<String, String> COLUMNS = buildColumns();

	private static Map<String, String> buildColumns() {
		Map<String, String> m = new LinkedHashMap<>();
		m.putAll(BulkImportHeaderAliases.templateColumnsEsForDownload());
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

	private void writeDataSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet(SHEET_DATA);
		CellStyle headerStyle = headerStyle(workbook);

		Row header = sheet.createRow(0);
		int col = 0;
		List<String> labels = new ArrayList<>(COLUMNS.values());
		for (String label : labels) {
			Cell cell = header.createCell(col);
			cell.setCellValue(label);
			cell.setCellStyle(headerStyle);
			col++;
		}

		Row sample = sheet.createRow(1);
		// Orden fijo = COLUMNS (sin "Nombre completo" / "Nombre completo apoderado")
		sample.createCell(0).setCellValue("DNI");
		sample.createCell(1).setCellValue("12345678");
		sample.createCell(2).setCellValue("Ada");
		sample.createCell(3).setCellValue("Lovelace");
		sample.createCell(4).setCellValue("Byron");
		sample.createCell(5).setCellValue("10/12/1815");
		sample.createCell(6).setCellValue("FEMENINO");
		sample.createCell(7).setCellValue("ada@example.com");
		sample.createCell(8).setCellValue("");
		sample.createCell(9).setCellValue("");
		sample.createCell(10).setCellValue("MATRICULADO");
		sample.createCell(11).setCellValue("01/03/2026");
		sample.createCell(12).setCellValue("DNI");
		sample.createCell(13).setCellValue("87654321");
		sample.createCell(14).setCellValue("Anne");
		sample.createCell(15).setCellValue("Isabella");
		sample.createCell(16).setCellValue("+51 999000111");
		sample.createCell(17).setCellValue("anne@example.com");
		sample.createCell(18).setCellValue("MADRE");

		if (COLUMNS.size() != 19) {
			throw new IllegalStateException(
					"Student template sample row must match column count; expected 19, got "
							+ COLUMNS.size());
		}
		for (int i = 0; i < COLUMNS.size(); i++) {
			sheet.setColumnWidth(i, 20 * 256);
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
		writeReferenceColumn(sheet, headerStyle, 2, "Estado de matrícula",
				List.of(EnrollmentStatus.values()).stream().map(Enum::name).toList());
		writeReferenceColumn(sheet, headerStyle, 3, "Parentesco",
				List.of(RelationshipType.values()).stream().map(Enum::name).toList());

		for (int i = 0; i < 4; i++) {
			sheet.setColumnWidth(i, 28 * 256);
		}

		int notesCol = 5;
		sheet.setColumnWidth(notesCol, 70 * 256);
		writeNote(sheet, headerStyle, notesCol, 0, "Cómo llenar la plantilla");
		writeNote(sheet, null, notesCol, 1,
				"1. Edita la hoja Estudiantes — conserva la fila de encabezados.");
		writeNote(sheet, null, notesCol, 2,
				"2. Obligatorios del alumno: Tipo de documento, Número de documento, Nombres, Apellido paterno.");
		writeNote(sheet, null, notesCol, 3,
				"3. Apoderado es opcional; si llenas alguno de sus campos, completa doc + nombres + apellido.");
		writeNote(sheet, null, notesCol, 4,
				"4. Fechas en formato día/mes/año (dd/MM/yyyy), p. ej. 01/03/2026.");
		writeNote(sheet, null, notesCol, 5,
				"5. La sección (p. ej. 1ro Primaria · A) se elige en la pantalla de importación, no en el Excel.");
		writeNote(sheet, null, notesCol, 6,
				"6. Valores de género: MASCULINO, FEMENINO, OTRO, NO_ESPECIFICADO (también MALE/FEMALE).");
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

	@SuppressWarnings("unused")
	private static final Class<?> GENDER_REF = Gender.class;
}
