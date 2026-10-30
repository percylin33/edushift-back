package com.edushift.modules.students.service.bulk;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.entity.RelationshipType;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudentTemplateGeneratorTest {

	private final StudentTemplateGenerator generator = new StudentTemplateGenerator();

	@Test
	@DisplayName("generated workbook has Spanish header row on Estudiantes sheet")
	void hasCanonicalHeader() throws Exception {
		byte[] payload = generator.generate();

		try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(payload))) {
			Sheet sheet = wb.getSheet("Estudiantes");
			assertThat(sheet).isNotNull();

			Row header = sheet.getRow(0);
			List<String> labels = readRow(header);
			assertThat(labels).contains(
					"Tipo de documento", "Número de documento",
					"Nombres", "Apellido paterno",
					"Nombres apoderado", "Teléfono apoderado", "Parentesco");
		}
	}

	@Test
	@DisplayName("Referencia sheet lists document types and parentesco")
	void referenceMirrorsEnums() throws Exception {
		byte[] payload = generator.generate();

		try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(payload))) {
			Sheet ref = wb.getSheet("Referencia");
			assertThat(ref).isNotNull();

			assertThat(columnValues(ref, 0)).containsExactlyInAnyOrder(
					namesOf(DocumentType.values()));
			assertThat(columnValues(ref, 2)).containsExactlyInAnyOrder(
					namesOf(EnrollmentStatus.values()));
			assertThat(columnValues(ref, 3)).containsExactlyInAnyOrder(
					namesOf(RelationshipType.values()));
		}
	}

	@Test
	@DisplayName("the sample row uses values that pass the parser's coercion")
	void sampleRowParses() throws Exception {
		byte[] payload = generator.generate();

		StudentBulkImportParser parser = new StudentBulkImportParser();
		List<StudentRowDraft> drafts = parser.parse(new ByteArrayInputStream(payload));

		assertThat(drafts).hasSize(1);
		StudentRowDraft sample = drafts.get(0);
		assertThat(sample.documentType()).isEqualTo(DocumentType.DNI);
		assertThat(sample.firstName()).isEqualTo("Ada");
		assertThat(sample.gender()).isEqualTo(Gender.FEMALE);
		assertThat(sample.enrollmentStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
		assertThat(sample.guardianFirstName()).isEqualTo("Anne");
		assertThat(sample.guardianRelationship()).isEqualTo(RelationshipType.MOTHER);
	}

	private static List<String> readRow(Row row) {
		DataFormatter df = new DataFormatter();
		java.util.List<String> result = new java.util.ArrayList<>();
		for (int c = 0; c < row.getLastCellNum(); c++) {
			Cell cell = row.getCell(c);
			result.add(cell == null ? "" : df.formatCellValue(cell));
		}
		while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
			result.remove(result.size() - 1);
		}
		return result;
	}

	private static Set<String> columnValues(Sheet sheet, int columnIndex) {
		DataFormatter df = new DataFormatter();
		Set<String> values = new HashSet<>();
		for (int r = 1; r <= sheet.getLastRowNum(); r++) {
			Row row = sheet.getRow(r);
			if (row == null) {
				continue;
			}
			Cell cell = row.getCell(columnIndex);
			if (cell == null) {
				continue;
			}
			String v = df.formatCellValue(cell);
			if (v != null && !v.isBlank()) {
				values.add(v);
			}
		}
		return values;
	}

	private static String[] namesOf(Enum<?>[] values) {
		String[] names = new String[values.length];
		for (int i = 0; i < values.length; i++) {
			names[i] = values[i].name();
		}
		return names;
	}
}
