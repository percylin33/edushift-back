package com.edushift.modules.teachers.bulk;

import com.edushift.modules.students.dto.BulkImportPreviewResponse;
import com.edushift.modules.students.service.bulk.BulkImportDates;
import com.edushift.modules.students.service.bulk.BulkImportEnumAliases;
import com.edushift.modules.students.service.bulk.BulkImportException;
import com.edushift.modules.students.service.bulk.BulkImportHeaderAliases;
import com.edushift.modules.teachers.dto.TeacherRowDraft;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Parses a teacher bulk-import spreadsheet (.xlsx) into {@link TeacherRowDraft}s.
 * Supports auto alias resolution or an explicit {@code columnMapping}
 * (canonical → exact Excel header) from the FE mapping wizard.
 */
@Component
public class TeacherBulkImportParser {

	private static final int PREVIEW_SAMPLE_ROWS = 5;
	private static final List<String> REQUIRED_COLUMNS = TeacherBulkImportHeaderAliases.required();

	private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

	public List<TeacherRowDraft> parse(InputStream xlsxStream) {
		return parse(xlsxStream, null);
	}

	public List<TeacherRowDraft> parse(InputStream xlsxStream, Map<String, String> columnMapping) {
		try (Workbook workbook = new XSSFWorkbook(xlsxStream)) {
			if (workbook.getNumberOfSheets() == 0) {
				throw new BulkImportException("INVALID_FILE", "Workbook has no sheets");
			}
			Sheet sheet = workbook.getSheetAt(0);
			HeaderLayout layout = resolveLayout(sheet, columnMapping);
			validateRequiredColumns(layout.canonicalToIndex());

			List<TeacherRowDraft> drafts = new ArrayList<>();
			int firstDataRow = layout.headerRowIndex() + 1;
			int lastRow = sheet.getLastRowNum();
			for (int r = firstDataRow; r <= lastRow; r++) {
				Row row = sheet.getRow(r);
				if (row == null || isRowBlank(row, layout.canonicalToIndex())) {
					continue;
				}
				drafts.add(toDraft(row, layout.canonicalToIndex()));
			}
			return drafts;
		}
		catch (IOException e) {
			throw new BulkImportException("INVALID_FILE", "Could not read spreadsheet", e);
		}
		catch (BulkImportException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw new BulkImportException("INVALID_FILE",
					"Spreadsheet is malformed: " + e.getMessage(), e);
		}
	}

	public BulkImportPreviewResponse preview(InputStream xlsxStream) {
		try (Workbook workbook = new XSSFWorkbook(xlsxStream)) {
			if (workbook.getNumberOfSheets() == 0) {
				throw new BulkImportException("INVALID_FILE", "Workbook has no sheets");
			}
			Sheet sheet = workbook.getSheetAt(0);
			int headerRowIndex = detectHeaderRowIndex(sheet);
			List<String> headers = readExactHeaders(sheet, headerRowIndex);
			if (headers.isEmpty()) {
				throw new BulkImportException("INVALID_FILE", "Spreadsheet has no header row");
			}

			Map<String, String> suggested = TeacherBulkImportHeaderAliases.suggestMapping(headers);
			List<String> unmapped = TeacherBulkImportHeaderAliases.unmappedRequired(suggested);
			Map<String, Integer> exactToIndex = buildExactToIndex(sheet, headerRowIndex);

			List<Map<String, String>> sampleRows = new ArrayList<>();
			int firstDataRow = headerRowIndex + 1;
			int lastRow = sheet.getLastRowNum();
			for (int r = firstDataRow; r <= lastRow && sampleRows.size() < PREVIEW_SAMPLE_ROWS; r++) {
				Row row = sheet.getRow(r);
				if (row == null || isRowBlankByExact(row, exactToIndex)) {
					continue;
				}
				Map<String, String> sample = new LinkedHashMap<>();
				for (String header : headers) {
					Integer idx = exactToIndex.get(header);
					sample.put(header, idx == null ? null : readCellAsString(row.getCell(idx)));
				}
				sampleRows.add(sample);
			}
			return new BulkImportPreviewResponse(headers, sampleRows, suggested, unmapped);
		}
		catch (IOException e) {
			throw new BulkImportException("INVALID_FILE", "Could not read spreadsheet", e);
		}
		catch (BulkImportException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw new BulkImportException("INVALID_FILE",
					"Spreadsheet is malformed: " + e.getMessage(), e);
		}
	}

	private HeaderLayout resolveLayout(Sheet sheet, Map<String, String> columnMapping) {
		int headerRowIndex = detectHeaderRowIndex(sheet);
		List<String> exactHeaders = readExactHeaders(sheet, headerRowIndex);
		if (exactHeaders.isEmpty()) {
			throw new BulkImportException("INVALID_FILE", "Spreadsheet has no header row");
		}
		Map<String, Integer> exactToIndex = buildExactToIndex(sheet, headerRowIndex);
		Map<String, Integer> canonicalToIndex = new LinkedHashMap<>();

		if (columnMapping != null && !columnMapping.isEmpty()) {
			for (Map.Entry<String, String> e : columnMapping.entrySet()) {
				String canonical = e.getKey();
				String exactHeader = e.getValue();
				if (exactHeader == null || exactHeader.isBlank()) {
					continue;
				}
				Integer idx = exactToIndex.get(exactHeader);
				if (idx == null) {
					throw new BulkImportException("UNKNOWN_COLUMN",
							"Mapped header not found in spreadsheet: " + exactHeader);
				}
				if (canonicalToIndex.containsKey(canonical)) {
					throw new BulkImportException("INVALID_FILE",
							"Duplicate column mapping for " + canonical);
				}
				canonicalToIndex.put(canonical, idx);
			}
		}
		else {
			for (Map.Entry<String, Integer> e : exactToIndex.entrySet()) {
				String canonical = TeacherBulkImportHeaderAliases.resolveCanonical(e.getKey());
				if (canonical == null) {
					continue;
				}
				canonicalToIndex.putIfAbsent(canonical, e.getValue());
			}
		}
		return new HeaderLayout(headerRowIndex, exactHeaders, canonicalToIndex);
	}

	int detectHeaderRowIndex(Sheet sheet) {
		int first = sheet.getFirstRowNum();
		int last = Math.min(sheet.getLastRowNum(), first + 40);
		int bestRow = first;
		int bestScore = Integer.MIN_VALUE;
		for (int r = first; r <= last; r++) {
			Row row = sheet.getRow(r);
			if (row == null) {
				continue;
			}
			int aliasHits = 0;
			int nonempty = 0;
			for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
				Cell cell = row.getCell(c);
				if (cell == null) {
					continue;
				}
				String name = formatter.formatCellValue(cell).trim();
				if (name.isEmpty()) {
					continue;
				}
				nonempty++;
				if (TeacherBulkImportHeaderAliases.resolveCanonical(name) != null) {
					aliasHits++;
				}
			}
			int score = (aliasHits * 100) + nonempty;
			if (score > bestScore) {
				bestScore = score;
				bestRow = r;
			}
		}
		return bestRow;
	}

	private Map<String, Integer> buildExactToIndex(Sheet sheet, int headerRowIndex) {
		Map<String, Integer> map = new LinkedHashMap<>();
		Row headerRow = sheet.getRow(headerRowIndex);
		if (headerRow == null) {
			return map;
		}
		for (int c = headerRow.getFirstCellNum(); c < headerRow.getLastCellNum(); c++) {
			Cell cell = headerRow.getCell(c);
			if (cell == null) {
				continue;
			}
			String name = formatter.formatCellValue(cell).trim();
			if (name.isEmpty()) {
				continue;
			}
			map.putIfAbsent(name, c);
		}
		return map;
	}

	private List<String> readExactHeaders(Sheet sheet, int headerRowIndex) {
		Row header = sheet.getRow(headerRowIndex);
		if (header == null) {
			return List.of();
		}
		List<String> headers = new ArrayList<>();
		for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
			Cell cell = header.getCell(c);
			if (cell == null) {
				continue;
			}
			String name = formatter.formatCellValue(cell).trim();
			if (!name.isEmpty()) {
				headers.add(name);
			}
		}
		return headers;
	}

	private void validateRequiredColumns(Map<String, Integer> columns) {
		List<String> missing = new ArrayList<>();
		for (String required : REQUIRED_COLUMNS) {
			if (!columns.containsKey(required)) {
				missing.add(TeacherBulkImportHeaderAliases.displayLabelEs(required));
			}
		}
		if (!missing.isEmpty()) {
			throw new BulkImportException("INVALID_FILE",
					"Faltan columnas obligatorias: " + String.join(", ", missing));
		}
	}

	private boolean isRowBlank(Row row, Map<String, Integer> columns) {
		for (Integer idx : columns.values()) {
			String value = readCellAsString(row.getCell(idx));
			if (value != null && !value.isBlank()) {
				return false;
			}
		}
		return true;
	}

	private boolean isRowBlankByExact(Row row, Map<String, Integer> exactToIndex) {
		for (Integer idx : exactToIndex.values()) {
			String value = readCellAsString(row.getCell(idx));
			if (value != null && !value.isBlank()) {
				return false;
			}
		}
		return true;
	}

	private TeacherRowDraft toDraft(Row row, Map<String, Integer> columns) {
		int rowNumber = row.getRowNum() + 1;
		Map<String, String> raw = new HashMap<>();
		for (Map.Entry<String, Integer> e : columns.entrySet()) {
			raw.put(e.getKey(), readCellAsString(row.getCell(e.getValue())));
		}

		LocalDate birthDate = readLocalDate(row, columns, BulkImportHeaderAliases.BIRTH_DATE);
		LocalDate hireDate = readLocalDate(row, columns, TeacherBulkImportHeaderAliases.HIRE_DATE);

		return new TeacherRowDraft(
				rowNumber,
				BulkImportEnumAliases.parseDocumentType(
						raw.get(BulkImportHeaderAliases.DOCUMENT_TYPE)),
				trimToNull(raw.get(BulkImportHeaderAliases.DOCUMENT_NUMBER)),
				trimToNull(raw.get(BulkImportHeaderAliases.FIRST_NAME)),
				trimToNull(raw.get(BulkImportHeaderAliases.LAST_NAME)),
				trimToNull(raw.get(BulkImportHeaderAliases.SECOND_LAST_NAME)),
				birthDate,
				BulkImportEnumAliases.parseGender(raw.get(BulkImportHeaderAliases.GENDER)),
				trimToNull(raw.get(BulkImportHeaderAliases.EMAIL)),
				trimToNull(raw.get(BulkImportHeaderAliases.PHONE)),
				trimToNull(raw.get(TeacherBulkImportHeaderAliases.TITLE)),
				trimToNull(raw.get(TeacherBulkImportHeaderAliases.SPECIALIZATIONS)),
				hireDate,
				BulkImportEnumAliases.parseEmploymentStatus(
						raw.get(TeacherBulkImportHeaderAliases.EMPLOYMENT_STATUS)));
	}

	private String readCellAsString(Cell cell) {
		if (cell == null) {
			return null;
		}
		return switch (cell.getCellType()) {
			case BLANK -> null;
			case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
			case NUMERIC -> {
				if (DateUtil.isCellDateFormatted(cell)) {
					yield formatter.formatCellValue(cell);
				}
				double v = cell.getNumericCellValue();
				if (v == Math.floor(v) && !Double.isInfinite(v)) {
					yield Long.toString((long) v);
				}
				yield Double.toString(v);
			}
			case STRING -> cell.getStringCellValue();
			case FORMULA -> formatter.formatCellValue(cell);
			default -> formatter.formatCellValue(cell);
		};
	}

	private LocalDate readLocalDate(Row row, Map<String, Integer> columns, String key) {
		Integer idx = columns.get(key);
		if (idx == null) {
			return null;
		}
		Cell cell = row.getCell(idx);
		if (cell == null) {
			return null;
		}
		return switch (cell.getCellType()) {
			case BLANK -> null;
			case NUMERIC -> {
				if (DateUtil.isCellDateFormatted(cell)) {
					yield cell.getLocalDateTimeCellValue() == null ? null
							: cell.getLocalDateTimeCellValue().toLocalDate();
				}
				yield BulkImportDates.parse(formatter.formatCellValue(cell));
			}
			case STRING -> BulkImportDates.parse(cell.getStringCellValue());
			case FORMULA -> BulkImportDates.parse(formatter.formatCellValue(cell));
			default -> null;
		};
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	public static List<String> requiredColumns() {
		return List.copyOf(REQUIRED_COLUMNS);
	}

	private record HeaderLayout(
			int headerRowIndex,
			List<String> exactHeaders,
			Map<String, Integer> canonicalToIndex
	) {
	}
}
