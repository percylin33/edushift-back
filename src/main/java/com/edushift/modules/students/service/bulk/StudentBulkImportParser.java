package com.edushift.modules.students.service.bulk;

import com.edushift.modules.students.dto.BulkImportPreviewResponse;
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
 * Parses a student bulk-import spreadsheet (.xlsx) into
 * {@link StudentRowDraft} rows.
 *
 * <p>Headers may be English camelCase, Spanish labels, or near-synonyms
 * (see {@link BulkImportHeaderAliases}). Callers may also pass an
 * explicit {@code canonical → exactHeader} mapping from the FE wizard.
 */
@Component
public class StudentBulkImportParser {

	private static final int PREVIEW_SAMPLE_ROWS = 5;

	private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

	public List<StudentRowDraft> parse(InputStream xlsxStream) {
		return parse(xlsxStream, null);
	}

	/**
	 * @param columnMapping canonical field → exact header text from the
	 *                      spreadsheet; {@code null} uses alias resolution
	 */
	public List<StudentRowDraft> parse(InputStream xlsxStream,
			Map<String, String> columnMapping) {
		try (Workbook workbook = new XSSFWorkbook(xlsxStream)) {
			Sheet sheet = requireSheet(workbook);
			HeaderLayout layout = resolveLayout(sheet, columnMapping);
			validateRequiredColumns(layout.canonicalToIndex());

			List<StudentRowDraft> drafts = new ArrayList<>();
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
			throw new BulkImportException("INVALID_FILE",
					"Could not read spreadsheet", e);
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
			Sheet sheet = requireSheet(workbook);
			int headerRowIndex = detectHeaderRowIndex(sheet);
			List<String> headers = readExactHeaders(sheet, headerRowIndex);
			if (headers.isEmpty()) {
				throw new BulkImportException("INVALID_FILE",
						"Spreadsheet has no header row");
			}

			Map<String, String> suggested = BulkImportHeaderAliases.suggestMapping(headers);
			List<String> unmapped = BulkImportHeaderAliases.unmappedRequired(suggested);

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
			throw new BulkImportException("INVALID_FILE",
					"Could not read spreadsheet", e);
		}
		catch (BulkImportException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw new BulkImportException("INVALID_FILE",
					"Spreadsheet is malformed: " + e.getMessage(), e);
		}
	}

	// ---------------------------------------------------------------------------
	// Layout
	// ---------------------------------------------------------------------------

	private Sheet requireSheet(Workbook workbook) {
		if (workbook.getNumberOfSheets() == 0) {
			throw new BulkImportException("INVALID_FILE", "Workbook has no sheets");
		}
		return workbook.getSheetAt(0);
	}

	private HeaderLayout resolveLayout(Sheet sheet, Map<String, String> columnMapping) {
		int headerRowIndex = detectHeaderRowIndex(sheet);
		if (columnMapping != null && !columnMapping.isEmpty()) {
			var mappedHeaders = columnMapping.values().stream()
					.filter(value -> value != null && !value.isBlank()).toList();
			if (!mappedHeaders.isEmpty()) {
				int last = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + 40);
				for (int row = sheet.getFirstRowNum(); row <= last; row++) {
					if (readExactHeaders(sheet, row).containsAll(mappedHeaders)) {
						headerRowIndex = row;
						break;
					}
				}
			}
		}
		List<String> exactHeaders = readExactHeaders(sheet, headerRowIndex);
		if (exactHeaders.isEmpty()) {
			throw new BulkImportException("INVALID_FILE",
					"Spreadsheet has no header row");
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
				String canonical = BulkImportHeaderAliases.resolveCanonical(e.getKey());
				if (canonical == null) {
					continue;
				}
				if (canonicalToIndex.containsKey(canonical)) {
					throw new BulkImportException("INVALID_FILE",
							"Duplicate column mapping for " + canonical);
				}
				canonicalToIndex.put(canonical, e.getValue());
			}
		}
		return new HeaderLayout(headerRowIndex, exactHeaders, canonicalToIndex);
	}

	/**
	 * Finds the header row when the sheet has title/subtitle rows above the
	 * real column names (common in school “nómina” exports). Prefers the row
	 * with the most known alias hits; falls back to the densest multi-cell row.
	 */
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
			int nonempty = 0;
			int aliasHits = 0;
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
				if (BulkImportHeaderAliases.resolveCanonical(name) != null) {
					aliasHits++;
				}
			}
			if (nonempty == 0) {
				continue;
			}
			// Title rows are typically a single merged cell — deprioritize.
			int score = (aliasHits * 100) + nonempty;
			if (nonempty == 1) {
				score -= 50;
			}
			if (score > bestScore) {
				bestScore = score;
				bestRow = r;
			}
		}
		return bestRow;
	}

	private Map<String, Integer> buildExactToIndex(Sheet sheet, int headerRowIndex) {
		Map<String, Integer> exactToIndex = new LinkedHashMap<>();
		Row headerRow = sheet.getRow(headerRowIndex);
		if (headerRow == null) {
			return exactToIndex;
		}
		for (int c = headerRow.getFirstCellNum(); c < headerRow.getLastCellNum(); c++) {
			Cell cell = headerRow.getCell(c);
			if (cell == null) {
				continue;
			}
			String exact = formatter.formatCellValue(cell).trim();
			if (!exact.isEmpty()) {
				exactToIndex.putIfAbsent(exact, c);
			}
		}
		return exactToIndex;
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
		if (!columns.containsKey(BulkImportHeaderAliases.DOCUMENT_TYPE)) {
			missing.add("Tipo de documento (documentType)");
		}
		if (!columns.containsKey(BulkImportHeaderAliases.DOCUMENT_NUMBER)) {
			missing.add("Número de documento (documentNumber)");
		}
		boolean hasSplitNames = columns.containsKey(BulkImportHeaderAliases.FIRST_NAME)
				&& columns.containsKey(BulkImportHeaderAliases.LAST_NAME);
		boolean hasFullName = columns.containsKey(BulkImportHeaderAliases.FULL_NAME);
		if (!hasSplitNames && !hasFullName) {
			missing.add("Nombres/Apellido (firstName+lastName) o Nombre completo (fullName)");
		}
		if (!missing.isEmpty()) {
			throw new BulkImportException("INVALID_FILE",
					"Missing required columns: " + String.join(", ", missing));
		}
	}

	// ---------------------------------------------------------------------------
	// Row → draft
	// ---------------------------------------------------------------------------

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

	private StudentRowDraft toDraft(Row row, Map<String, Integer> columns) {
		int rowNumber = row.getRowNum() + 1;
		Map<String, String> raw = new HashMap<>();
		for (Map.Entry<String, Integer> e : columns.entrySet()) {
			raw.put(e.getKey(), readCellAsString(row.getCell(e.getValue())));
		}

		String firstName = trimToNull(raw.get(BulkImportHeaderAliases.FIRST_NAME));
		String lastName = trimToNull(raw.get(BulkImportHeaderAliases.LAST_NAME));
		String secondLastName = trimToNull(raw.get(BulkImportHeaderAliases.SECOND_LAST_NAME));
		if ((firstName == null || lastName == null)
				&& trimToNull(raw.get(BulkImportHeaderAliases.FULL_NAME)) != null) {
			String[] split = splitFullName(raw.get(BulkImportHeaderAliases.FULL_NAME));
			if (firstName == null) {
				firstName = split[0];
			}
			if (lastName == null) {
				lastName = split[1];
			}
			if (secondLastName == null && split.length > 2) {
				secondLastName = split[2];
			}
		}

		String guardianFirst = trimToNull(raw.get(BulkImportHeaderAliases.GUARDIAN_FIRST_NAME));
		String guardianLast = trimToNull(raw.get(BulkImportHeaderAliases.GUARDIAN_LAST_NAME));
		if ((guardianFirst == null || guardianLast == null)
				&& trimToNull(raw.get(BulkImportHeaderAliases.GUARDIAN_FULL_NAME)) != null) {
			String[] split = splitFullName(raw.get(BulkImportHeaderAliases.GUARDIAN_FULL_NAME));
			if (guardianFirst == null) {
				guardianFirst = split[0];
			}
			if (guardianLast == null) {
				guardianLast = split[1];
			}
		}

		return new StudentRowDraft(
				rowNumber,
				BulkImportEnumAliases.parseDocumentType(raw.get(BulkImportHeaderAliases.DOCUMENT_TYPE)),
				trimToNull(raw.get(BulkImportHeaderAliases.DOCUMENT_NUMBER)),
				firstName,
				lastName,
				secondLastName,
				readLocalDate(row, columns, BulkImportHeaderAliases.BIRTH_DATE),
				BulkImportEnumAliases.parseGender(raw.get(BulkImportHeaderAliases.GENDER)),
				trimToNull(raw.get(BulkImportHeaderAliases.EMAIL)),
				trimToNull(raw.get(BulkImportHeaderAliases.PHONE)),
				trimToNull(raw.get(BulkImportHeaderAliases.ADDRESS)),
				BulkImportEnumAliases.parseEnrollmentStatus(
						raw.get(BulkImportHeaderAliases.ENROLLMENT_STATUS)),
				readLocalDate(row, columns, BulkImportHeaderAliases.ENROLLMENT_DATE),
				BulkImportEnumAliases.parseDocumentType(
						raw.get(BulkImportHeaderAliases.GUARDIAN_DOCUMENT_TYPE)),
				trimToNull(raw.get(BulkImportHeaderAliases.GUARDIAN_DOCUMENT_NUMBER)),
				guardianFirst,
				guardianLast,
				trimToNull(raw.get(BulkImportHeaderAliases.GUARDIAN_PHONE)),
				trimToNull(raw.get(BulkImportHeaderAliases.GUARDIAN_EMAIL)),
				BulkImportEnumAliases.parseRelationship(
						raw.get(BulkImportHeaderAliases.GUARDIAN_RELATIONSHIP))
		);
	}

	/**
	 * Supports {@code "Apellidos, Nombres"} and {@code "Nombres Apellido1 Apellido2"}.
	 * Returns {@code [firstName, lastName, secondLastName?]}.
	 */
	static String[] splitFullName(String raw) {
		String value = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
		if (value.isEmpty()) {
			return new String[] { null, null };
		}
		if (value.contains(",")) {
			String[] parts = value.split(",", 2);
			String last = parts[0].trim();
			String first = parts.length > 1 ? parts[1].trim() : null;
			String[] lastParts = last.split(" ");
			if (lastParts.length >= 2) {
				return new String[] {
						blankToNull(first),
						blankToNull(lastParts[0]),
						blankToNull(String.join(" ",
								java.util.Arrays.copyOfRange(lastParts, 1, lastParts.length)))
				};
			}
			return new String[] { blankToNull(first), blankToNull(last) };
		}
		String[] tokens = value.split(" ");
		if (tokens.length == 1) {
			return new String[] { tokens[0], tokens[0] };
		}
		if (tokens.length == 2) {
			return new String[] { tokens[0], tokens[1] };
		}
		// Assume last two tokens are surnames (common ES/PE naming).
		return new String[] {
				String.join(" ", java.util.Arrays.copyOfRange(tokens, 0, tokens.length - 2)),
				tokens[tokens.length - 2],
				tokens[tokens.length - 1]
		};
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
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
				yield tryParseDate(formatter.formatCellValue(cell));
			}
			case STRING -> tryParseDate(cell.getStringCellValue());
			case FORMULA -> tryParseDate(formatter.formatCellValue(cell));
			default -> null;
		};
	}

	private LocalDate tryParseDate(String value) {
		return BulkImportDates.parse(value);
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	public static List<String> requiredColumns() {
		return BulkImportHeaderAliases.studentRequired().stream()
				.map(s -> s.toLowerCase(Locale.ROOT))
				.toList();
	}

	public static List<String> knownColumns() {
		return BulkImportHeaderAliases.allCanonical().stream()
				.map(s -> s.toLowerCase(Locale.ROOT))
				.toList();
	}

	private record HeaderLayout(
			int headerRowIndex,
			List<String> exactHeaders,
			Map<String, Integer> canonicalToIndex
	) {
	}
}
