package com.edushift.modules.students.service.bulk;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Parses a student bulk-import spreadsheet (.xlsx) into a list of
 * {@link StudentRowDraft}.
 *
 * <h3>Required columns (case-insensitive, header row mandatory)</h3>
 * <ul>
 *   <li>{@code documentType}</li>
 *   <li>{@code documentNumber}</li>
 *   <li>{@code firstName}</li>
 *   <li>{@code lastName}</li>
 * </ul>
 *
 * <h3>Optional columns</h3>
 * <ul>
 *   <li>{@code secondLastName}, {@code birthDate}, {@code gender},
 *       {@code email}, {@code phone}, {@code address},
 *       {@code enrollmentStatus}, {@code enrollmentDate}</li>
 * </ul>
 *
 * <p>Empty rows (every cell blank) are silently skipped — admins often
 * leave trailing blank lines after the last record.
 *
 * <h3>Coercion failures = per-row errors, not aborts</h3>
 * If a row has a malformed enum or date the parser still produces a
 * {@link StudentRowDraft} with that field set to {@code null}; the
 * runner then runs the validation pass and reports a {@code ROW_INVALID}
 * error for that row. Aborting the whole job because of a bad
 * {@code DNZ} typo would be hostile to admins importing 100 rows.
 */
@Component
public class StudentBulkImportParser {

	private static final List<String> REQUIRED_COLUMNS = List.of(
			"documenttype", "documentnumber", "firstname", "lastname");

	private static final List<String> KNOWN_COLUMNS = List.of(
			"documenttype", "documentnumber",
			"firstname", "lastname", "secondlastname",
			"birthdate", "gender",
			"email", "phone", "address",
			"enrollmentstatus", "enrollmentdate");

	private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

	/**
	 * Parse the spreadsheet from the given input stream. Closes the
	 * stream as part of the workbook lifecycle.
	 */
	public List<StudentRowDraft> parse(InputStream xlsxStream) {
		try (Workbook workbook = new XSSFWorkbook(xlsxStream)) {
			if (workbook.getNumberOfSheets() == 0) {
				throw new BulkImportException(
						"INVALID_FILE", "Workbook has no sheets");
			}
			Sheet sheet = workbook.getSheetAt(0);
			Map<String, Integer> columns = readHeader(sheet);
			validateRequiredColumns(columns);

			List<StudentRowDraft> drafts = new ArrayList<>();
			int firstDataRow = sheet.getFirstRowNum() + 1;
			int lastRow = sheet.getLastRowNum();
			for (int r = firstDataRow; r <= lastRow; r++) {
				Row row = sheet.getRow(r);
				if (row == null || isRowBlank(row, columns)) {
					continue;
				}
				drafts.add(toDraft(row, columns));
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
			// POI surfaces zip / parser failures as RuntimeException;
			// surface them uniformly as INVALID_FILE so the worker can
			// mark the job FAILED with a stable code.
			throw new BulkImportException("INVALID_FILE",
					"Spreadsheet is malformed: " + e.getMessage(), e);
		}
	}

	// ---------------------------------------------------------------------------
	// Header
	// ---------------------------------------------------------------------------

	private Map<String, Integer> readHeader(Sheet sheet) {
		Row header = sheet.getRow(sheet.getFirstRowNum());
		if (header == null) {
			throw new BulkImportException("INVALID_FILE",
					"Spreadsheet has no header row");
		}
		Map<String, Integer> columns = new LinkedHashMap<>();
		for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
			Cell cell = header.getCell(c);
			if (cell == null) continue;
			String name = formatter.formatCellValue(cell).trim().toLowerCase(Locale.ROOT);
			if (name.isEmpty()) continue;
			columns.putIfAbsent(name, c);
		}
		return columns;
	}

	private void validateRequiredColumns(Map<String, Integer> columns) {
		List<String> missing = new ArrayList<>();
		for (String required : REQUIRED_COLUMNS) {
			if (!columns.containsKey(required)) {
				missing.add(required);
			}
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

	private StudentRowDraft toDraft(Row row, Map<String, Integer> columns) {
		// 1-based row number (header = 1, first data = 2) — what an
		// admin sees in the spreadsheet's row gutter.
		int rowNumber = row.getRowNum() + 1;
		Map<String, String> raw = new HashMap<>();
		for (Map.Entry<String, Integer> e : columns.entrySet()) {
			raw.put(e.getKey(), readCellAsString(row.getCell(e.getValue())));
		}

		LocalDate birthDate = readLocalDate(row, columns, "birthdate");
		LocalDate enrollmentDate = readLocalDate(row, columns, "enrollmentdate");

		return new StudentRowDraft(
				rowNumber,
				parseEnum(raw.get("documenttype"), DocumentType.class),
				trimToNull(raw.get("documentnumber")),
				trimToNull(raw.get("firstname")),
				trimToNull(raw.get("lastname")),
				trimToNull(raw.get("secondlastname")),
				birthDate,
				parseEnum(raw.get("gender"), Gender.class),
				trimToNull(raw.get("email")),
				trimToNull(raw.get("phone")),
				trimToNull(raw.get("address")),
				parseEnum(raw.get("enrollmentstatus"), EnrollmentStatus.class),
				enrollmentDate
		);
	}

	private String readCellAsString(Cell cell) {
		if (cell == null) return null;
		return switch (cell.getCellType()) {
			case BLANK -> null;
			case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
			case NUMERIC -> {
				if (DateUtil.isCellDateFormatted(cell)) {
					// Push date handling into readLocalDate; here just
					// return the formatted text so caller doesn't crash.
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
		if (idx == null) return null;
		Cell cell = row.getCell(idx);
		if (cell == null) return null;
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
		if (value == null || value.isBlank()) return null;
		String trimmed = value.trim();
		try {
			return LocalDate.parse(trimmed);   // ISO yyyy-MM-dd
		}
		catch (RuntimeException ignored) {
			// Fall through to validation, where it'll surface as
			// ROW_INVALID with a helpful message.
			return null;
		}
	}

	private static <E extends Enum<E>> E parseEnum(String value, Class<E> type) {
		return parseEnum(value, type, Function.identity());
	}

	private static <E extends Enum<E>> E parseEnum(String value, Class<E> type,
			Function<String, String> normalizer) {
		String s = trimToNull(value);
		if (s == null) return null;
		try {
			return Enum.valueOf(type, normalizer.apply(s).toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static String trimToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	// ---------------------------------------------------------------------------
	// Public for tests
	// ---------------------------------------------------------------------------

	public static List<String> requiredColumns() {
		return List.copyOf(REQUIRED_COLUMNS);
	}

	public static List<String> knownColumns() {
		return List.copyOf(KNOWN_COLUMNS);
	}
}
