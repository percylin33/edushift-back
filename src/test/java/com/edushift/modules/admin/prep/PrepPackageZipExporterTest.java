package com.edushift.modules.admin.prep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.modules.students.service.bulk.StudentTemplateGenerator;
import com.edushift.modules.teachers.bulk.TeacherTemplateGenerator;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PrepPackageZipExporterTest {

	private final PrepPackageZipExporter exporter = new PrepPackageZipExporter(
			new PrepPackagePdfRenderer(),
			new PrepPackageWorkbookFactory(),
			new StudentTemplateGenerator(),
			new TeacherTemplateGenerator());

	@Test
	@DisplayName("ZIP includes personalized guide PDF and PILOT omits payment concepts")
	void pilotZipContents() throws Exception {
		PrepPackageDownload pack = exporter.export("Colegio San Marcos", PrepPackageVariant.PILOT);
		assertThat(pack.fileName()).isEqualTo("prep-datos-colegio-san-marcos.zip");
		assertThat(pack.bytes()).isNotEmpty();

		Set<String> names = zipEntryNames(pack.bytes());
		assertThat(names).anyMatch(n -> n.contains("01-Guia-preparacion-Colegio San Marcos.pdf"));
		assertThat(names).anyMatch(n -> n.endsWith("02-Anexo-Piloto.pdf"));
		assertThat(names).anyMatch(n -> n.endsWith("03-Checklist-archivos.pdf"));
		assertThat(names).anyMatch(n -> n.endsWith("LEEME.txt"));
		assertThat(names).anyMatch(n -> n.endsWith("plantillas/06-estudiantes-apoderados.xlsx"));
		assertThat(names).anyMatch(n -> n.endsWith("plantillas/07-asignaciones-docente.xlsx"));
		assertThat(names).noneMatch(n -> n.contains("08-conceptos-pago"));
		assertThat(names).noneMatch(n -> n.contains("07-conceptos-pago"));

		String readme = readZipEntry(pack.bytes(), names.stream()
				.filter(n -> n.endsWith("LEEME.txt"))
				.findFirst()
				.orElseThrow());
		assertThat(readme).contains("Colegio: Colegio San Marcos");
		assertThat(readme).contains("Variante: Piloto");
		assertThat(readme).contains("07-asignaciones-docente.xlsx");
		assertThat(readme).doesNotContain("SIAGIE", "SEED_", "centimos");

		String guide = pdfText(pack.bytes(), names.stream()
				.filter(n -> n.contains("01-Guia-preparacion"))
				.findFirst()
				.orElseThrow());
		String annex = pdfText(pack.bytes(), names.stream()
				.filter(n -> n.endsWith("02-Anexo-Piloto.pdf"))
				.findFirst()
				.orElseThrow());
		assertThat(guide).contains("MATRIZ DE RESPONSABLES");
		assertThat(guide).contains("BLOQUE B");
		assertThat(guide + annex).doesNotContain("SIAGIE", "SEED_", "centimos", "/learning-sessions");
	}

	@Test
	@DisplayName("PRODUCTION ZIP includes payment concepts workbook")
	void productionIncludesPayments() throws Exception {
		PrepPackageDownload pack = exporter.export("Demo", PrepPackageVariant.PRODUCTION);
		Set<String> names = zipEntryNames(pack.bytes());
		assertThat(names).anyMatch(n -> n.endsWith("plantillas/07-asignaciones-docente.xlsx"));
		assertThat(names).anyMatch(n -> n.endsWith("plantillas/08-conceptos-pago.xlsx"));
		assertThat(names).anyMatch(n -> n.endsWith("02-Anexo-Produccion.pdf"));
		String readme = readZipEntry(pack.bytes(), names.stream()
				.filter(n -> n.endsWith("LEEME.txt"))
				.findFirst()
				.orElseThrow());
		assertThat(readme).contains("08-conceptos-pago.xlsx");
		assertThat(readme).contains("soles");
		assertThat(readme).doesNotContain("centimos");
	}

	@Nested
	class Slugify {
		@Test
		void stripsAccentsAndSpaces() {
			assertThat(PrepPackageZipExporter.slugify("Colegio San Marcos"))
					.isEqualTo("colegio-san-marcos");
			assertThat(PrepPackageZipExporter.slugify("  ")).isEqualTo("colegio");
		}
	}

	@Nested
	class VariantParse {
		@Test
		void acceptsAliases() {
			assertThat(PrepPackageVariant.fromParam(null)).isEqualTo(PrepPackageVariant.PRODUCTION);
			assertThat(PrepPackageVariant.fromParam("piloto")).isEqualTo(PrepPackageVariant.PILOT);
			assertThat(PrepPackageVariant.fromParam("PRODUCCION")).isEqualTo(PrepPackageVariant.PRODUCTION);
		}

		@Test
		void rejectsUnknown() {
			assertThatThrownBy(() -> PrepPackageVariant.fromParam("BETA"))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	private static Set<String> zipEntryNames(byte[] zipBytes) throws Exception {
		Set<String> names = new HashSet<>();
		try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zin.getNextEntry()) != null) {
				names.add(entry.getName());
			}
		}
		return names;
	}

	private static String pdfText(byte[] zipBytes, String entryName) throws IOException {
		return textOf(readZipEntryBytes(zipBytes, entryName));
	}

	private static String textOf(byte[] pdf) throws IOException {
		PdfReader reader = new PdfReader(pdf);
		try {
			PdfTextExtractor extractor = new PdfTextExtractor(reader);
			StringBuilder text = new StringBuilder();
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				text.append(extractor.getTextFromPage(page)).append('\n');
			}
			return text.toString();
		}
		finally {
			reader.close();
		}
	}

	private static byte[] readZipEntryBytes(byte[] zipBytes, String entryName) throws IOException {
		try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zin.getNextEntry()) != null) {
				if (entryName.equals(entry.getName())) {
					return zin.readAllBytes();
				}
			}
		}
		throw new AssertionError("Missing entry: " + entryName);
	}

	private static String readZipEntry(byte[] zipBytes, String entryName) throws Exception {
		try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zin.getNextEntry()) != null) {
				if (entryName.equals(entry.getName())) {
					return new String(zin.readAllBytes(), StandardCharsets.UTF_8);
				}
			}
		}
		throw new AssertionError("Missing entry: " + entryName);
	}
}
