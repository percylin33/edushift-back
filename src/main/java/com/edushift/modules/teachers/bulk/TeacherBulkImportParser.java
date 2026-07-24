package com.edushift.modules.teachers.bulk;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.service.bulk.BulkImportException;
import com.edushift.modules.teachers.dto.TeacherRowDraft;
import com.edushift.modules.teachers.entity.EmploymentStatus;
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
 * Parses a teacher bulk-import spreadsheet (.xlsx) into a list of
 * {@link TeacherRowDraft} (Sprint cierre-B / F7).
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
 *       {@code email}, {@code phone}, {@code title},
 *       {@code specializations} (comma-separated, e.g. "Matemática, Física"),
 *       {@code hireDate}, {@code employmentStatus}</li>
 * </ul>
 *
 * <p>The shape mirrors {@code StudentBulkImportParser} so the admin
 * upload UX is identical between students and teachers — same file
 * layout, same error handling. Bad rows are reported per-row, the
 * whole import never aborts on one malformed {@code documentType}.</p>
 */
@Component
public class TeacherBulkImportParser {

	private static final List<String> REQUIRED_COLUMNS = List.of(
			"documenttype", "documentnumber", "firstname", "lastname");

	private static final List<String> KNOWN_COLUMNS = List.of(
			"documenttype", "documentnumber",
			"firstname", "lastname", "secondlastname",
			"birthdate", "gender",
			"email", "phone", "title", "specializations",
			"hiredate", "employmentstatus");

	private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

	public List<TeacherRowDraft> parse(InputStream xlsxStream) {
		try (Workbook workbook = new XSSFWorkbook(xlsxStream)) {
			if (workbook.getNumberOfSheets() == 0) {
				throw new BulkImportException("INVALID_FILE", "Workbook has no sheets");
			}
			Sheet sheet = workbook.getSheetAt(0);
			Map<String, Integer> columns = readHeader(sheet);
			validateRequiredColumns(columns);

			List<TeacherRowDraft> drafts = new ArrayList<>();
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

	private Map<String, Integer> readHeader(Sheet sheet) {
		Row header = sheet.getRow(sheet.getFirstRowNum());
		if (header == null) {
			throw new BulkImportException("INVALID_FILE", "Spreadsheet has no header row");
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

	private boolean isRowBlank(Row row, Map<String, Integer> columns) {
		for (Integer idx : columns.values()) {
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

		LocalDate birthDate = readLocalDate(row, columns, "birthdate");
		LocalDate hireDate = readLocalDate(row, columns, "hiredate");

		return new TeacherRowDraft(
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
				trimToNull(raw.get("title")),
				trimToNull(raw.get("specializations")),
				hireDate,
				parseEnum(raw.get("employmentstatus"), EmploymentStatus.class));
	}

	private String readCellAsString(Cell cell) {
		if (cell == null) return null;
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
			return LocalDate.parse(trimmed);
		}
		catch (RuntimeException ignored) {
			return null;
		}
	}

	private static <E extends Enum<E>> E parseEnum(String value, Class<E> type) {
		String s = trimToNull(value);
		if (s == null) return null;
		try {
			return Enum.valueOf(type, s.toUpperCase(Locale.ROOT));
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

	public static List<String> requiredColumns() {
		return List.copyOf(REQUIRED_COLUMNS);
	}

	public static List<String> knownColumns() {
		return List.copyOf(KNOWN_COLUMNS);
	}
}