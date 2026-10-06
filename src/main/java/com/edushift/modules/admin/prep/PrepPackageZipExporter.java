package com.edushift.modules.admin.prep;

import com.edushift.modules.students.service.bulk.StudentTemplateGenerator;
import com.edushift.modules.teachers.bulk.TeacherTemplateGenerator;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembles the personalized school data-prep ZIP (PDFs + Excel templates).
 */
@Component
@RequiredArgsConstructor
public class PrepPackageZipExporter {

	private final PrepPackagePdfRenderer pdfRenderer;
	private final PrepPackageWorkbookFactory workbooks;
	private final StudentTemplateGenerator studentTemplateGenerator;
	private final TeacherTemplateGenerator teacherTemplateGenerator;

	public PrepPackageDownload export(String schoolName, PrepPackageVariant variant) {
		String folder = "prep-datos-" + slugify(schoolName);
		String zipName = folder + ".zip";
		String safeDisplay = sanitizeFileSegment(schoolName);

		ByteArrayOutputStream zipOut = new ByteArrayOutputStream(128 * 1024);
		try (ZipOutputStream zip = new ZipOutputStream(zipOut, StandardCharsets.UTF_8)) {
			putDir(zip, folder + "/");
			putDir(zip, folder + "/plantillas/");

			putBytes(zip, folder + "/01-Guia-preparacion-" + safeDisplay + ".pdf",
					pdfRenderer.renderGuide(schoolName, variant));
			putBytes(zip, folder + "/02-Anexo-" + variant.fileLabel() + ".pdf",
					pdfRenderer.renderAnnex(schoolName, variant));
			putBytes(zip, folder + "/03-Checklist-archivos.pdf",
					pdfRenderer.renderChecklist(schoolName));
			putBytes(zip, folder + "/LEEME.txt", readme(schoolName, variant).getBytes(StandardCharsets.UTF_8));

			putBytes(zip, folder + "/plantillas/01-institucion.xlsx",
					workbooks.institution(schoolName));
			putBytes(zip, folder + "/plantillas/02-calendario-academico.xlsx",
					workbooks.calendar());
			putBytes(zip, folder + "/plantillas/03-secciones.xlsx",
					workbooks.sections());
			putBytes(zip, folder + "/plantillas/04-cursos.xlsx",
					workbooks.courses());
			putBytes(zip, folder + "/plantillas/05-docentes.xlsx",
					teacherTemplateGenerator.generate());
			putBytes(zip, folder + "/plantillas/06-estudiantes-apoderados.xlsx",
					studentTemplateGenerator.generate());
			putBytes(zip, folder + "/plantillas/07-asignaciones-docente.xlsx",
					workbooks.assignments());
			if (variant == PrepPackageVariant.PRODUCTION) {
				putBytes(zip, folder + "/plantillas/08-conceptos-pago.xlsx",
						workbooks.paymentConcepts());
			}

			zip.finish();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Prep package ZIP failed: " + ex.getMessage(), ex);
		}
		return new PrepPackageDownload(zipOut.toByteArray(), zipName);
	}

	private static String readme(String schoolName, PrepPackageVariant variant) {
		String payments = variant == PrepPackageVariant.PRODUCTION
				? """
				08-conceptos-pago.xlsx         — matrícula, pensión y otros cobros, monto en soles
				"""
				: """
				(Esta variante no incluye conceptos de pago.)
				""";
		return """
				EduShift — Pack de preparación de datos
				=======================================
				Colegio: %s
				Variante: %s

				Para quién es
				-------------
				Dirección, secretaría y coordinación académica.
				El colegio queda en español, con hora de Lima y montos en soles.

				Contenido
				---------
				01-Guia-preparacion-*.pdf  — qué enviar y quién lo llena
				02-Anexo-*.pdf             — lo mínimo del piloto, o el padrón completo
				03-Checklist-archivos.pdf  — logo, fondo e icono
				plantillas/                — hojas Excel para completar

				Plantillas
				----------
				01-institucion.xlsx            — nombre, nombre corto de la web y color
				02-calendario-academico.xlsx   — año y periodos (esas fechas marcan el periodo en curso)
				03-secciones.xlsx              — nivel, grado y letra (se ve como 1.º · A)
				04-cursos.xlsx                 — código, nombre y niveles
				05-docentes.xlsx               — la misma hoja que luego se sube en la plataforma
				06-estudiantes-apoderados.xlsx — la misma hoja que luego se sube; la sección se elige al importar
				07-asignaciones-docente.xlsx   — qué docente dicta cada curso, en qué sección y periodo
				%s
				Competencias y unidades no van en este paquete.
				Cuando el curso ya esté cargado, el administrador abre Matemática
				y Comunicación y usa Cargar programa del MINEDU. En los demás cursos
				el coordinador las escribe en la ficha. Las unidades se crean ahí.
				Sin la hoja de asignaciones, el docente no verá sus cursos al preparar clases.

				Cómo devolver
				-------------
				1. Complete las hojas. No borre la fila de títulos y reemplace la fila de ejemplo.
				2. Adjunte el logo y, si los tiene, el fondo y el icono (vea el checklist).
				3. Envíe esta carpeta a su contacto de EduShift.
				""".formatted(schoolName, variant.labelEs(), payments);
	}

	private static void putDir(ZipOutputStream zip, String path) throws Exception {
		zip.putNextEntry(new ZipEntry(path));
		zip.closeEntry();
	}

	private static void putBytes(ZipOutputStream zip, String path, byte[] bytes) throws Exception {
		zip.putNextEntry(new ZipEntry(path));
		zip.write(bytes);
		zip.closeEntry();
	}

	/** URL/slug-ish folder + zip base name. */
	static String slugify(String raw) {
		if (raw == null || raw.isBlank()) {
			return "colegio";
		}
		String nfd = Normalizer.normalize(raw.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
		StringBuilder sb = new StringBuilder(nfd.length());
		for (int i = 0; i < nfd.length(); i++) {
			char c = nfd.charAt(i);
			if (Character.getType(c) == Character.NON_SPACING_MARK) {
				continue;
			}
			if (Character.isLetterOrDigit(c)) {
				sb.append(c);
			}
			else if (c == ' ' || c == '_' || c == '-') {
				if (sb.isEmpty() || sb.charAt(sb.length() - 1) == '-') {
					continue;
				}
				sb.append('-');
			}
		}
		String slug = sb.toString().replaceAll("^-+|-+$", "");
		if (slug.isEmpty()) {
			return "colegio";
		}
		return slug.length() > 60 ? slug.substring(0, 60) : slug;
	}

	static String sanitizeFileSegment(String raw) {
		if (raw == null || raw.isBlank()) {
			return "colegio";
		}
		String cleaned = raw.trim()
				.replaceAll("[\\\\/:*?\"<>|]", "-")
				.replaceAll("\\s+", " ")
				.trim();
		if (cleaned.isEmpty()) {
			return "colegio";
		}
		return cleaned.length() > 80 ? cleaned.substring(0, 80).trim() : cleaned;
	}
}
