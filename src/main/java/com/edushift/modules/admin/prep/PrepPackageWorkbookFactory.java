package com.edushift.modules.admin.prep;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
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
 * Builds intake Excel templates (headers ES + sample row + notes sheet).
 */
@Component
public class PrepPackageWorkbookFactory {

	byte[] institution(String schoolName) {
		return simpleSheet("Institucion",
				List.of("Nombre oficial", "Nombre corto para la web", "Correo de quien administrará",
						"Nombres", "Apellidos", "Color principal",
						"Color secundario (opcional)", "Teléfono de soporte (opcional)"),
				List.of(schoolName, "colegio-san-marcos", "directora@colegio.edu.pe", "María",
						"López", "#1E3A5F", "", "+51 999000111"),
				List.of(
						"Obligatorios: nombre oficial, nombre corto, correo y nombres.",
						"Nombre corto: solo minúsculas, números y guiones. Ejemplo: colegio-san-marcos.",
						"Es la dirección del colegio en EduShift.",
						"Color principal: puede pegar un código como #1E3A5F o describir el color (azul marino).",
						"El colegio queda en español, con hora de Lima y montos en soles."));
	}

	byte[] calendar() {
		return simpleSheet("Calendario",
				List.of("Nombre del año", "Inicio del año (aaaa-mm-dd)", "Fin del año (aaaa-mm-dd)",
						"Tipo de periodo", "Número de orden", "Nombre del periodo (opcional)",
						"Inicio del periodo", "Fin del periodo"),
				List.of("2026", "2026-03-01", "2026-12-15", "BIMESTRE", "1", "I Bimestre",
						"2026-03-01", "2026-05-15"),
				List.of(
						"Una fila por bimestre, trimestre o por el año completo.",
						"Repita el nombre del año y sus fechas de inicio y fin en cada fila.",
						"Tipo de periodo: escriba BIMESTRE, TRIMESTRE o ANUAL.",
						"El número de orden empieza en 1.",
						"Esas fechas indican qué periodo está en curso en la plataforma."));
	}

	byte[] sections() {
		return simpleSheet("Secciones",
				List.of("Nivel (INICIAL/PRIMARIA/SECUNDARIA)", "Grado", "Letra de la sección",
						"Cupo (opcional)", "Documento del tutor (opcional)",
						"Nombre del tutor (opcional)"),
				List.of("SECUNDARIA", "1ro", "A", "35", "", ""),
				List.of(
						"La letra suele ser A, B o C.",
						"En la plataforma se ve como grado y letra, por ejemplo 1.º · A.",
						"El cupo es opcional.",
						"Si indica tutor, use el mismo número de documento que en la hoja de docentes."));
	}

	byte[] courses() {
		return simpleSheet("Cursos",
				List.of("Código", "Nombre", "Niveles (separados por ;)",
						"Horas semanales (opcional)", "Color (opcional)", "Activo (SI/NO)"),
				List.of("MAT", "Matemática", "PRIMARIA;SECUNDARIA", "5", "#2563EB", "SI"),
				List.of(
						"El código es único en el colegio (MAT, COMU, EF).",
						"Niveles: uno o más de INICIAL, PRIMARIA, SECUNDARIA, separados por punto y coma.",
						"No envíe competencias ni unidades en esta hoja.",
						"Cuando el curso ya esté en la plataforma, el administrador abre Matemática (MAT) y Comunicación (COMU) y usa Cargar programa del MINEDU.",
						"En los demás cursos, el coordinador escribe competencias y unidades en la ficha del curso."));
	}

	byte[] assignments() {
		return simpleSheet("Asignaciones",
				List.of("Documento del docente", "Código del curso", "Nivel", "Grado", "Sección",
						"Periodo (nombre o número)", "Activo (SI/NO)"),
				List.of("45678912", "MAT", "SECUNDARIA", "1ro", "A", "I Bimestre", "SI"),
				List.of(
						"Una fila = este docente dicta este curso en esta sección durante este periodo.",
						"Repita la fila por cada bimestre o trimestre en que lo dicta.",
						"El documento debe coincidir con la hoja de docentes.",
						"El código del curso debe coincidir con la hoja de cursos.",
						"Nivel, grado y sección deben coincidir con la hoja de secciones.",
						"Sin estas filas el docente no verá sus cursos al preparar clases."));
	}

	byte[] paymentConcepts() {
		return simpleSheet("ConceptosPago",
				List.of("Código", "Nombre", "Tipo", "Monto en soles",
						"Descripción (opcional)", "Se repite cada periodo (SI/NO)", "Activo (SI/NO)"),
				List.of("MATRICULA", "Matrícula 2026", "Matrícula", "350.00",
						"Matrícula anual", "NO", "SI"),
				List.of(
						"El monto va en soles. Ejemplo: 350.00 es trescientos cincuenta soles.",
						"Tipo, escriba uno de estos nombres:",
						"Matrícula, Pensión, Útiles, Alimentación, Movilidad, Examen, Evento, Otro.",
						"Para cargarlo en la plataforma, el tipo corresponde a: Matrícula=ENROLLMENT, Pensión=TUITION, Útiles=MATERIALS, Alimentación=MEALS, Movilidad=TRANSPORT, Examen=EXAM, Evento=EVENT, Otro=OTHER.",
						"Si el colegio no cobrará por la plataforma al empezar, puede dejar esta hoja vacía (conserve los títulos)."));
	}

	private byte[] simpleSheet(
			String sheetName,
			List<String> headers,
			List<String> sample,
			List<String> notes
	) {
		try (Workbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet data = workbook.createSheet(sheetName);
			CellStyle headerStyle = headerStyle(workbook);

			Row headerRow = data.createRow(0);
			for (int i = 0; i < headers.size(); i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(headers.get(i));
				cell.setCellStyle(headerStyle);
				data.setColumnWidth(i, 22 * 256);
			}

			Row sampleRow = data.createRow(1);
			for (int i = 0; i < sample.size(); i++) {
				sampleRow.createCell(i).setCellValue(sample.get(i));
			}
			data.createFreezePane(0, 1);

			Sheet ref = workbook.createSheet("Referencia");
			CellStyle noteHeader = headerStyle(workbook);
			Row rh = ref.createRow(0);
			Cell hc = rh.createCell(0);
			hc.setCellValue("Como llenar");
			hc.setCellStyle(noteHeader);
			ref.setColumnWidth(0, 80 * 256);
			for (int i = 0; i < notes.size(); i++) {
				ref.createRow(i + 1).createCell(0).setCellValue(notes.get(i));
			}

			workbook.write(out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not generate prep template " + sheetName, e);
		}
	}

	private static CellStyle headerStyle(Workbook workbook) {
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
}
