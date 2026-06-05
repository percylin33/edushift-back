package com.edushift.modules.students.service.bulk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudentBulkImportParserTest {

	private final StudentBulkImportParser parser = new StudentBulkImportParser();

	@Test
	@DisplayName("happy path — parses a 2-row spreadsheet with all columns populated")
	void happyPath() throws Exception {
		byte[] payload = build(workbook -> {
			Sheet sheet = workbook.createSheet("Students");
			writeHeader(sheet,
					"documentType", "documentNumber", "firstName", "lastName",
					"secondLastName", "birthDate", "gender",
					"email", "phone", "address", "enrollmentStatus", "enrollmentDate");
			Row r = sheet.createRow(1);
			r.createCell(0).setCellValue("DNI");
			r.createCell(1).setCellValue("12345678");
			r.createCell(2).setCellValue("Ada");
			r.createCell(3).setCellValue("Lovelace");
			r.createCell(4).setCellValue("Byron");
			r.createCell(5).setCellValue("1815-12-10");
			r.createCell(6).setCellValue("FEMALE");
			r.createCell(7).setCellValue("ada@example.com");
			r.createCell(8).setCellValue("+51 999");
			r.createCell(9).setCellValue("Acme");
			r.createCell(10).setCellValue("ENROLLED");
			r.createCell(11).setCellValue("2026-03-01");
		});

		List<StudentRowDraft> drafts = parser.parse(new ByteArrayInputStream(payload));

		assertThat(drafts).hasSize(1);
		StudentRowDraft d = drafts.get(0);
		assertThat(d.rowNumber()).isEqualTo(2);
		assertThat(d.documentType()).isEqualTo(DocumentType.DNI);
		assertThat(d.documentNumber()).isEqualTo("12345678");
		assertThat(d.firstName()).isEqualTo("Ada");
		assertThat(d.lastName()).isEqualTo("Lovelace");
		assertThat(d.secondLastName()).isEqualTo("Byron");
		assertThat(d.birthDate()).isEqualTo(LocalDate.of(1815, 12, 10));
		assertThat(d.gender()).isEqualTo(Gender.FEMALE);
		assertThat(d.email()).isEqualTo("ada@example.com");
		assertThat(d.phone()).isEqualTo("+51 999");
		assertThat(d.enrollmentStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
		assertThat(d.enrollmentDate()).isEqualTo(LocalDate.of(2026, 3, 1));
	}

	@Test
	@DisplayName("missing required column → BulkImportException(INVALID_FILE)")
	void missingRequiredColumn() throws Exception {
		byte[] payload = build(workbook -> {
			Sheet sheet = workbook.createSheet("Students");
			writeHeader(sheet, "documentType", "firstName", "lastName");
		});

		assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(payload)))
				.isInstanceOfSatisfying(BulkImportException.class,
						ex -> {
							assertThat(ex.getCode()).isEqualTo("INVALID_FILE");
							assertThat(ex.getMessage()).contains("documentnumber");
						});
	}

	@Test
	@DisplayName("trailing blank rows are silently skipped")
	void blankRowsSkipped() throws Exception {
		byte[] payload = build(workbook -> {
			Sheet sheet = workbook.createSheet("Students");
			writeHeader(sheet,
					"documentType", "documentNumber", "firstName", "lastName");
			Row r = sheet.createRow(1);
			r.createCell(0).setCellValue("DNI");
			r.createCell(1).setCellValue("11111111");
			r.createCell(2).setCellValue("Ada");
			r.createCell(3).setCellValue("Lovelace");

			// rows 2..5 left blank: should not produce drafts
			sheet.createRow(2);
			Row maybeBlank = sheet.createRow(3);
			maybeBlank.createCell(0).setCellValue("");
			sheet.createRow(4);
		});

		List<StudentRowDraft> drafts = parser.parse(new ByteArrayInputStream(payload));

		assertThat(drafts).hasSize(1);
		assertThat(drafts.get(0).rowNumber()).isEqualTo(2);
	}

	@Test
	@DisplayName("unparsable enum/date → field is null on the draft, no exception")
	void coercionFailureIsNullField() throws Exception {
		byte[] payload = build(workbook -> {
			Sheet sheet = workbook.createSheet("Students");
			writeHeader(sheet,
					"documentType", "documentNumber", "firstName", "lastName",
					"birthDate", "gender", "enrollmentStatus");
			Row r = sheet.createRow(1);
			r.createCell(0).setCellValue("Frob"); // unknown enum
			r.createCell(1).setCellValue("11111111");
			r.createCell(2).setCellValue("Ada");
			r.createCell(3).setCellValue("Lovelace");
			r.createCell(4).setCellValue("not-a-date");
			r.createCell(5).setCellValue("Apache");
			r.createCell(6).setCellValue("ROAMING");
		});

		List<StudentRowDraft> drafts = parser.parse(new ByteArrayInputStream(payload));

		StudentRowDraft d = drafts.get(0);
		assertThat(d.documentType()).isNull();
		assertThat(d.birthDate()).isNull();
		assertThat(d.gender()).isNull();
		assertThat(d.enrollmentStatus()).isNull();
		// required text fields are still trimmed and present
		assertThat(d.firstName()).isEqualTo("Ada");
	}

	@Test
	@DisplayName("numeric documentNumber from Excel is preserved as digits")
	void numericDocumentNumber() throws Exception {
		byte[] payload = build(workbook -> {
			Sheet sheet = workbook.createSheet("Students");
			writeHeader(sheet,
					"documentType", "documentNumber", "firstName", "lastName");
			Row r = sheet.createRow(1);
			r.createCell(0).setCellValue("DNI");
			Cell numCell = r.createCell(1);
			numCell.setCellValue(12345678d);
			r.createCell(2).setCellValue("Ada");
			r.createCell(3).setCellValue("Lovelace");
		});

		List<StudentRowDraft> drafts = parser.parse(new ByteArrayInputStream(payload));

		assertThat(drafts.get(0).documentNumber()).isEqualTo("12345678");
	}

	@Test
	@DisplayName("Excel-formatted date cell parses to the right LocalDate")
	void excelDateCellParses() throws Exception {
		byte[] payload = build(workbook -> {
			Sheet sheet = workbook.createSheet("Students");
			writeHeader(sheet,
					"documentType", "documentNumber",
					"firstName", "lastName", "birthDate");
			CreationHelper helper = workbook.getCreationHelper();
			CellStyle dateStyle = workbook.createCellStyle();
			dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

			Row r = sheet.createRow(1);
			r.createCell(0).setCellValue("DNI");
			r.createCell(1).setCellValue("11111111");
			r.createCell(2).setCellValue("Ada");
			r.createCell(3).setCellValue("Lovelace");
			Cell dateCell = r.createCell(4);
			dateCell.setCellValue(LocalDate.of(2026, 3, 1));
			dateCell.setCellStyle(dateStyle);
		});

		List<StudentRowDraft> drafts = parser.parse(new ByteArrayInputStream(payload));

		assertThat(drafts.get(0).birthDate()).isEqualTo(LocalDate.of(2026, 3, 1));
	}

	@Test
	@DisplayName("malformed file (not an xlsx) → INVALID_FILE")
	void malformedFile() {
		byte[] junk = "not-a-spreadsheet".getBytes();

		assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(junk)))
				.isInstanceOfSatisfying(BulkImportException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("INVALID_FILE"));
	}

	// ---------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------

	@FunctionalInterface
	interface WorkbookBuilder {
		void build(Workbook workbook) throws IOException;
	}

	private static byte[] build(WorkbookBuilder cb) throws IOException {
		try (Workbook wb = new XSSFWorkbook();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			cb.build(wb);
			wb.write(out);
			return out.toByteArray();
		}
	}

	private static void writeHeader(Sheet sheet, String... labels) {
		Row header = sheet.createRow(0);
		for (int i = 0; i < labels.length; i++) {
			header.createCell(i).setCellValue(labels[i]);
		}
	}
}
