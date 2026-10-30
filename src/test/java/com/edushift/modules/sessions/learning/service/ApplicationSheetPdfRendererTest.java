package com.edushift.modules.sessions.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.sessions.learning.entity.ApplicationSheet;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetKind;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ApplicationSheetPdfRendererTest {

	private final ApplicationSheetPdfRenderer renderer = new ApplicationSheetPdfRenderer();

	@Test
	void pdfIsOneGrade() {
		ApplicationSheet sheet = new ApplicationSheet();
		sheet.setKind(ApplicationSheetKind.LEARNING);
		sheet.setGrade("3.er Grado");
		sheet.setSheetNumber(1);
		sheet.setTitle("Contamos cómo nos fue en vacaciones");
		ApplicationSheetContent content = new ApplicationSheetContent();
		ApplicationSheetContent.Purpose purpose = new ApplicationSheetContent.Purpose();
		purpose.setPurpose("Compartir una anécdota.");
		purpose.setEvidence("Registro oral.");
		purpose.setCriterion("Identificó nombres y lugares.");
		content.setPurpose(purpose);
		sheet.setContent(content);

		byte[] pdf = renderer.render(sheet, "Comunicación", "Colegio Demo");

		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		assertThat(pdf.length).isGreaterThan(200);
		String body = new String(pdf, StandardCharsets.ISO_8859_1);
		assertThat(body).contains("3.er Grado");
		assertThat(body).doesNotContain("4.to Grado");
	}
}
