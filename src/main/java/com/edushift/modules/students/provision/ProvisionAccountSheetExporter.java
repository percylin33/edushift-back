package com.edushift.modules.students.provision;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Builds the printable credential sheet (xlsx) for CRED-7.
 */
@Component
public class ProvisionAccountSheetExporter {

	private static final String[] HEADERS = {
			"Sección", "Rol", "Nombre", "Documento", "Correo",
			"Contraseña temporal", "Origen correo", "Estado"
	};

	public byte[] toXlsx(String sectionName, List<ProvisionCredentialRow> rows) throws IOException {
		try (Workbook wb = new XSSFWorkbook();
				ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024)) {
			Sheet sheet = wb.createSheet("Cuentas");
			Row header = sheet.createRow(0);
			for (int i = 0; i < HEADERS.length; i++) {
				header.createCell(i).setCellValue(HEADERS[i]);
			}
			int rowIdx = 1;
			String section = sectionName == null ? "" : sectionName;
			for (ProvisionCredentialRow r : rows) {
				Row row = sheet.createRow(rowIdx++);
				row.createCell(0).setCellValue(section);
				row.createCell(1).setCellValue(nullSafe(r.role()));
				row.createCell(2).setCellValue(nullSafe(r.fullName()));
				row.createCell(3).setCellValue(doc(r));
				row.createCell(4).setCellValue(nullSafe(r.email()));
				row.createCell(5).setCellValue(nullSafe(r.temporaryPassword()));
				row.createCell(6).setCellValue(
						r.emailSource() == null ? "" : r.emailSource().name());
				row.createCell(7).setCellValue(nullSafe(r.status()));
			}
			for (int i = 0; i < HEADERS.length; i++) {
				sheet.autoSizeColumn(i);
			}
			wb.write(out);
			return out.toByteArray();
		}
	}

	private static String doc(ProvisionCredentialRow r) {
		String type = nullSafe(r.documentType());
		String number = nullSafe(r.documentNumber());
		if (type.isEmpty()) {
			return number;
		}
		if (number.isEmpty()) {
			return type;
		}
		return type + " " + number;
	}

	private static String nullSafe(String value) {
		return value == null ? "" : value;
	}
}
