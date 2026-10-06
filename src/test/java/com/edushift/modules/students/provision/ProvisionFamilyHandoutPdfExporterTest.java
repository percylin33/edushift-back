package com.edushift.modules.students.provision;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.auth.entity.EmailSource;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipInputStream;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProvisionFamilyHandoutPdfExporterTest {

	private final ProvisionFamilyHandoutPdfExporter exporter = new ProvisionFamilyHandoutPdfExporter();

	@Test
	@DisplayName("student sheet prints school, classroom, URL and password, without a QR")
	void rendersAccessSheetWithoutQr() throws Exception {
		UUID studentId = UUID.randomUUID();
		ProvisionCredentialRow student = row(
				"STUDENT",
				"Adrian Salazar Cruz",
				"salazar.71234575@colegio.accounts.edushift.local",
				"yy6eGX#S!GwdvZ",
				"CREATED",
				studentId,
				null);

		byte[] zip = exporter.toZip(
				"Colegio Virgen del Buen Paso",
				null,
				"3 años — A",
				"2026",
				"INICIAL",
				"https://colegio.example/auth/login",
				CredentialHandoutSettings.defaults(),
				List.of(student));

		byte[] pdf = firstPdf(zip);
		String text = textOf(pdf);

		assertThat(text).contains("Colegio Virgen del Buen Paso");
		assertThat(text).contains("Año Escolar 2026");
		assertThat(text).contains("Aula: 3 años — A");
		assertThat(text).contains("Nivel Inicial");
		assertThat(text).contains("Adrian Salazar Cruz");
		assertThat(text).contains("yy6eGX#S!GwdvZ");
		assertThat(text).contains("colegio.example/auth/login");
		assertThat(text).contains("No hay apoderado vinculado");
		assertThat(text.toUpperCase()).doesNotContain("QR");
		assertThat(text).doesNotContain("Acceso Rápido");

		PdfReader reader = new PdfReader(pdf);
		try {
			assertThat(reader.getPageN(1).getAsDict(PdfName.RESOURCES).getAsDict(PdfName.XOBJECT))
					.isNotNull();
		}
		finally {
			reader.close();
		}
	}

	@Test
	@DisplayName("linked tutor is printed and the missing-tutor warning is omitted")
	void rendersTutorCredentials() throws Exception {
		UUID studentId = UUID.randomUUID();
		ProvisionCredentialRow student = row(
				"STUDENT", "Ana Diaz", "ana@colegio.test", "TempAlumno1!",
				"CREATED", studentId, null);
		ProvisionCredentialRow tutor = row(
				"PARENT", "Maria Diaz", "maria@colegio.test", "TempTutor1!",
				"CREATED", studentId, UUID.randomUUID());

		byte[] pdf = firstPdf(exporter.toZip(
				"Colegio Norte",
				null,
				"1ro — A",
				"2026",
				"Primaria",
				"https://norte.example/login",
				CredentialHandoutSettings.defaults(),
				List.of(student, tutor)));
		String text = textOf(pdf);

		assertThat(text).contains("Maria Diaz");
		assertThat(text).contains("TempTutor1!");
		assertThat(text).contains("Nivel Primaria");
		assertThat(text).doesNotContain("No hay apoderado vinculado");
		assertThat(text.toUpperCase()).doesNotContain("QR");
	}

	private static ProvisionCredentialRow row(
			String role,
			String name,
			String email,
			String password,
			String status,
			UUID studentId,
			UUID guardianId
	) {
		return new ProvisionCredentialRow(
				role,
				name,
				"DNI",
				"12345678",
				email,
				password,
				EmailSource.SYNTHETIC,
				status,
				studentId,
				guardianId);
	}

	private static byte[] firstPdf(byte[] zip) throws IOException {
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			while (in.getNextEntry() != null) {
				byte[] bytes = in.readAllBytes();
				if (bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P') {
					return bytes;
				}
			}
		}
		throw new AssertionError("ZIP has no PDF");
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
}
