package com.edushift.modules.students.service.bulk;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * Date coercion for bulk-import spreadsheets. Preferred display/input
 * format is day/month/year ({@code dd/MM/yyyy}); ISO {@code yyyy-MM-dd}
 * remains accepted for compatibility.
 */
public final class BulkImportDates {

	private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter
			.ofPattern("d/M/uuuu")
			.withResolverStyle(ResolverStyle.STRICT)
			.withLocale(Locale.ROOT);

	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

	private BulkImportDates() {
	}

	/**
	 * @return parsed date, or {@code null} if blank / unparseable
	 */
	public static LocalDate parse(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		LocalDate dmy = tryFormat(trimmed, DAY_MONTH_YEAR);
		if (dmy != null) {
			return dmy;
		}
		return tryFormat(trimmed, ISO);
	}

	private static LocalDate tryFormat(String value, DateTimeFormatter formatter) {
		try {
			return LocalDate.parse(value, formatter);
		}
		catch (DateTimeParseException ignored) {
			return null;
		}
	}
}
