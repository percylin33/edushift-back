package com.edushift.modules.evaluations.gradebook;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookCategoryEntry;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookCellEntry;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookEvaluationEntry;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookResponse;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookStudentEntry;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class GradeBookActaExporterTest {

	@Test
	void xlsxUsesTheSchoolActaLayout() throws Exception {
		UUID categoryId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		UUID sessionTwoId = UUID.randomUUID();
		UUID examId = UUID.randomUUID();
		UUID studentId = UUID.randomUUID();
		GradeBookResponse gradeBook = new GradeBookResponse(
				UUID.randomUUID(),
				UUID.randomUUID(),
				"1° A",
				UUID.randomUUID(),
				"1° Primaria",
				1,
				"PRIMARIA",
				UUID.randomUUID(),
				"Comunicación",
				"García Pérez, Ana",
				UUID.randomUUID(),
				PeriodType.BIMESTRE,
				3,
				"3° Bimestre",
				null,
				null,
				EvaluationScale.SCORE_0_20,
				List.of(new GradeBookCategoryEntry(
						categoryId, "Cuaderno", "amber", 1, BigDecimal.ONE, true, true)),
				List.of(new GradeBookStudentEntry(
						studentId,
						"ARIAS QUISPE YANIRE",
						new BigDecimal("16"),
						"ACTIVE",
						Map.of(categoryId, new BigDecimal("15")),
						null,
						Map.of())),
				List.of(
						eval(sessionId, "S1", categoryId, "Cuaderno"),
						eval(sessionTwoId, "S2", categoryId, "Cuaderno"),
						eval(examId, "Examen", null, null)),
				List.of(new GradeBookCellEntry(
						studentId, sessionId, new BigDecimal("18"), null, Instant.parse("2026-08-01T00:00:00Z"))));

		byte[] bytes = GradeBookActaExporter.toXlsx(gradeBook, "2026");
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			XSSFSheet sheet = workbook.getSheetAt(0);
			assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("REGISTRO DE NOTAS - 2026");
			assertThat(sheet.getRow(2).getCell(0).getCellStyle().getFont().getFontName()).isEqualTo("Arial Black");
			assertThat(sheet.getRow(2).getCell(0).getCellStyle().getFont().getFontHeightInPoints()).isEqualTo((short) 28);
			assertThat(sheet.getRow(2).getHeightInPoints()).isEqualTo(42.75f);
			assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("3° BIMESTRE");
			assertThat(sheet.getRow(3).getHeightInPoints()).isEqualTo(72.75f);
			assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).contains("DOCENTE: García Pérez, Ana");
			assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).contains("CURSO: Comunicación");
			assertThat(sheet.getRow(5).getCell(0).getCellStyle().getFont().getFontHeightInPoints()).isEqualTo((short) 20);
			assertThat(sheet.getRow(7).getCell(1).getStringCellValue()).isEqualTo("APELLIDOS Y NOMBRES");
			assertThat(sheet.getRow(7).getCell(2).getStringCellValue()).isEqualTo("CUADERNO");
			assertThat(sheet.getRow(7).getCell(2).getCellStyle().getFont().getFontHeightInPoints()).isEqualTo((short) 22);
			assertThat(sheet.getRow(9).getCell(2).getStringCellValue()).isEqualTo("S1");
			assertThat(sheet.getRow(9).getCell(2).getCellStyle().getFont().getFontHeightInPoints()).isEqualTo((short) 18);
			assertThat(sheet.getRow(7).getCell(4).getStringCellValue()).isEqualTo("NIVEL DEL LOGRO");
			assertThat(sheet.getRow(7).getCell(4).getCellStyle().getRotation()).isEqualTo((short) 90);
			assertThat(sheet.getRow(7).getCell(6).getStringCellValue()).isEqualTo("PROMEDIO GENERAL");
			assertThat(sheet.getRow(10).getCell(1).getStringCellValue()).isEqualTo("ARIAS QUISPE YANIRE");
			assertThat(sheet.getRow(10).getCell(1).getCellStyle().getFont().getFontHeightInPoints()).isEqualTo((short) 22);
			assertThat(sheet.getRow(10).getHeightInPoints()).isEqualTo(37.5f);
			assertThat(sheet.getRow(10).getCell(2).getNumericCellValue()).isEqualTo(18d);
			assertThat(sheet.getRow(10).getCell(2).getCellStyle().getFont().getFontHeightInPoints()).isEqualTo((short) 20);
			assertThat(sheet.getRow(10).getCell(4).getNumericCellValue()).isEqualTo(15d);
			assertThat(sheet.getRow(10).getCell(6).getNumericCellValue()).isEqualTo(16d);
			assertThat(sheet.getColumnWidth(1)).isEqualTo((int) Math.round(88.71 * 256));
			assertThat(sheet.getColumnWidth(2)).isEqualTo((int) Math.round(7.71 * 256));
			assertThat(sheet.getPrintSetup().getLandscape()).isTrue();
			assertThat(sheet.getPrintSetup().getPaperSize()).isEqualTo(PrintSetup.A4_PAPERSIZE);
		}
	}

	private static GradeBookEvaluationEntry eval(UUID id, String name, UUID categoryId, String categoryName) {
		return new GradeBookEvaluationEntry(
				id,
				name,
				EvaluationKind.TASK,
				EvaluationScale.SCORE_0_20,
				EvaluationStatus.PUBLISHED,
				BigDecimal.ONE,
				null,
				categoryId,
				categoryName);
	}
}
