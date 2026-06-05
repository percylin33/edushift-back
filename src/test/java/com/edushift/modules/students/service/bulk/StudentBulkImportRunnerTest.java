package com.edushift.modules.students.service.bulk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.students.dto.CreateStudentRequest;
import com.edushift.modules.students.dto.StudentResponse;
import com.edushift.modules.students.entity.BulkImportJob;
import com.edushift.modules.students.entity.BulkImportStatus;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.repository.BulkImportJobRepository;
import com.edushift.modules.students.service.StudentService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.multitenancy.TenantContext;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit-level test of the bulk-import runner. Uses real Apache POI
 * spreadsheets crafted in memory and a real {@link Validator} so the
 * end-to-end coercion + validation + persistence path is exercised
 * without booting Spring.
 */
@ExtendWith(MockitoExtension.class)
class StudentBulkImportRunnerTest {

	@Mock private StudentService studentService;
	@Mock private BulkImportJobRepository jobRepository;

	private StudentBulkImportRunner runner;

	private static ValidatorFactory validatorFactory;

	@BeforeEach
	void setUp() {
		if (validatorFactory == null) {
			validatorFactory = Validation.buildDefaultValidatorFactory();
		}
		Validator validator = validatorFactory.getValidator();
		runner = new StudentBulkImportRunner(
				new StudentBulkImportParser(),
				studentService,
				jobRepository,
				validator);
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	@Test
	@DisplayName("happy path — every row persists, status COMPLETED, no errors")
	void allRowsValid() throws Exception {
		BulkImportJob job = newJob();
		when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(jobRepository.saveAndFlush(any(BulkImportJob.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(studentService.createStudent(any(CreateStudentRequest.class)))
				.thenAnswer(inv -> stubStudentResponse());

		byte[] payload = build(workbook -> {
			Sheet sheet = workbook.createSheet("Students");
			writeHeader(sheet,
					"documentType", "documentNumber", "firstName", "lastName");
			row(sheet, 1, "DNI", "11111111", "Anna", "Lovelace");
			row(sheet, 2, "DNI", "22222222", "Bob", "Smith");
		});

		runner.processJob(job.getId(), payload);

		assertThat(job.getStatus()).isEqualTo(BulkImportStatus.COMPLETED);
		assertThat(job.getTotalRows()).isEqualTo(2);
		assertThat(job.getProcessedRows()).isEqualTo(2);
		assertThat(job.getErrorRows()).isZero();
		assertThat(job.getErrorsView()).isEmpty();
		verify(studentService, times(2)).createStudent(any(CreateStudentRequest.class));
	}

	@Test
	@DisplayName("invalid spreadsheet shape — status FAILED, no rows attempted")
	void invalidShape() throws Exception {
		BulkImportJob job = newJob();
		when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(jobRepository.saveAndFlush(any(BulkImportJob.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		byte[] junk = "not a workbook".getBytes();

		runner.processJob(job.getId(), junk);

		assertThat(job.getStatus()).isEqualTo(BulkImportStatus.FAILED);
		assertThat(job.getFailReason()).contains("malformed");
		verify(studentService, never()).createStudent(any());
	}

	@Test
	@DisplayName("missing required column — status FAILED with parser message")
	void missingRequiredColumn() throws Exception {
		BulkImportJob job = newJob();
		when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(jobRepository.saveAndFlush(any(BulkImportJob.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		byte[] payload = build(workbook -> {
			Sheet sheet = workbook.createSheet("Students");
			writeHeader(sheet, "documentType", "firstName", "lastName");
		});

		runner.processJob(job.getId(), payload);

		assertThat(job.getStatus()).isEqualTo(BulkImportStatus.FAILED);
		assertThat(job.getFailReason()).contains("documentnumber");
		verify(studentService, never()).createStudent(any());
	}

	@Test
	@DisplayName("partial errors — coercion failure on row 2 keeps row 3 alive")
	void partialErrors() throws Exception {
		BulkImportJob job = newJob();
		when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(jobRepository.saveAndFlush(any(BulkImportJob.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(studentService.createStudent(any(CreateStudentRequest.class)))
				.thenAnswer(inv -> stubStudentResponse());

		byte[] payload = build(workbook -> {
			Sheet sheet = workbook.createSheet("Students");
			writeHeader(sheet,
					"documentType", "documentNumber", "firstName", "lastName");
			row(sheet, 1, "Frob", "11111111", "Anna", "Lovelace"); // bad documentType
			row(sheet, 2, "DNI", "22222222", "Bob", "Smith");
		});

		runner.processJob(job.getId(), payload);

		assertThat(job.getStatus()).isEqualTo(BulkImportStatus.COMPLETED);
		assertThat(job.getTotalRows()).isEqualTo(2);
		assertThat(job.getProcessedRows()).isEqualTo(2);
		assertThat(job.getErrorRows()).isEqualTo(1);
		assertThat(job.getErrorsView()).hasSize(1);
		assertThat(job.getErrorsView().get(0).row()).isEqualTo(2); // 1-based incl. header
		assertThat(job.getErrorsView().get(0).code()).isEqualTo("ROW_INVALID");
		verify(studentService, times(1)).createStudent(any());
	}

	@Test
	@DisplayName("in-batch duplicate document → ROW_DUPLICATE on the second occurrence")
	void inBatchDuplicateDocument() throws Exception {
		BulkImportJob job = newJob();
		when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(jobRepository.saveAndFlush(any(BulkImportJob.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(studentService.createStudent(any(CreateStudentRequest.class)))
				.thenAnswer(inv -> stubStudentResponse());

		byte[] payload = build(workbook -> {
			Sheet sheet = workbook.createSheet("Students");
			writeHeader(sheet,
					"documentType", "documentNumber", "firstName", "lastName");
			row(sheet, 1, "DNI", "11111111", "Anna", "Lovelace");
			row(sheet, 2, "DNI", "11111111", "Anna", "Lovelace"); // dup
		});

		runner.processJob(job.getId(), payload);

		assertThat(job.getErrorRows()).isEqualTo(1);
		assertThat(job.getErrorsView().get(0).code()).isEqualTo("ROW_DUPLICATE");
		assertThat(job.getErrorsView().get(0).row()).isEqualTo(3);
		// Only the first row is forwarded to the service; the second is short-circuited.
		verify(studentService, times(1)).createStudent(any());
	}

	@Test
	@DisplayName("createStudent throws ApiException — recorded as a row error, processing continues")
	void serviceConflictBecomesRowError() throws Exception {
		BulkImportJob job = newJob();
		when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(jobRepository.saveAndFlush(any(BulkImportJob.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(studentService.createStudent(any(CreateStudentRequest.class)))
				.thenThrow(new ConflictException("STUDENT_DOCUMENT_TAKEN", "row 1 conflict"))
				.thenReturn(stubStudentResponse());

		byte[] payload = build(workbook -> {
			Sheet sheet = workbook.createSheet("Students");
			writeHeader(sheet,
					"documentType", "documentNumber", "firstName", "lastName");
			row(sheet, 1, "DNI", "11111111", "Anna", "Lovelace");
			row(sheet, 2, "DNI", "22222222", "Bob", "Smith");
		});

		runner.processJob(job.getId(), payload);

		assertThat(job.getStatus()).isEqualTo(BulkImportStatus.COMPLETED);
		assertThat(job.getErrorRows()).isEqualTo(1);
		assertThat(job.getErrorsView().get(0).code()).isEqualTo("STUDENT_DOCUMENT_TAKEN");
		verify(studentService, times(2)).createStudent(any());
	}

	@Test
	@DisplayName("unknown jobId — short-circuits without crashing")
	void unknownJobId() throws Exception {
		UUID id = UUID.randomUUID();
		when(jobRepository.findById(id)).thenReturn(Optional.empty());

		runner.processJob(id, new byte[] {0});

		verify(jobRepository, never()).saveAndFlush(any(BulkImportJob.class));
		verify(studentService, never()).createStudent(any());
	}

	@Test
	@DisplayName("CreateStudentRequest carries normalised fields (gender/enrollmentStatus default)")
	void createRequestCarriesDefaults() throws Exception {
		BulkImportJob job = newJob();
		when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(jobRepository.saveAndFlush(any(BulkImportJob.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(studentService.createStudent(any(CreateStudentRequest.class)))
				.thenAnswer(inv -> stubStudentResponse());

		byte[] payload = build(workbook -> {
			Sheet sheet = workbook.createSheet("Students");
			writeHeader(sheet,
					"documentType", "documentNumber", "firstName", "lastName");
			row(sheet, 1, "DNI", "11111111", "Anna", "Lovelace");
		});

		runner.processJob(job.getId(), payload);

		ArgumentCaptor<CreateStudentRequest> captor =
				ArgumentCaptor.forClass(CreateStudentRequest.class);
		verify(studentService).createStudent(captor.capture());
		CreateStudentRequest sent = captor.getValue();
		assertThat(sent.documentType()).isEqualTo(DocumentType.DNI);
		assertThat(sent.gender()).isEqualTo(Gender.NOT_SPECIFIED);
	}

	// ---------------------------------------------------------------------------
	// Fixtures + helpers
	// ---------------------------------------------------------------------------

	private static BulkImportJob newJob() {
		BulkImportJob job = new BulkImportJob();
		setIdViaReflection(job, UUID.randomUUID());
		job.setPublicUuid(UUID.randomUUID());
		job.setJobType(com.edushift.modules.students.entity.BulkImportJobType.STUDENTS);
		job.setFileName("upload.xlsx");
		job.setFileSizeBytes(1024);
		return job;
	}

	private static StudentResponse stubStudentResponse() {
		return new StudentResponse(
				UUID.randomUUID(),
				DocumentType.DNI, "00000000",
				"X", "Y", null, "X Y",
				null, Gender.NOT_SPECIFIED,
				null, null, null,
				com.edushift.modules.students.entity.EnrollmentStatus.PENDING, null,
				null, new HashMap<>(), null, null);
	}

	private static void setIdViaReflection(Object entity, UUID id) {
		try {
			Class<?> clazz = entity.getClass();
			while (clazz != null) {
				try {
					Field f = clazz.getDeclaredField("id");
					f.setAccessible(true);
					f.set(entity, id);
					return;
				}
				catch (NoSuchFieldException ignored) {
					clazz = clazz.getSuperclass();
				}
			}
			throw new IllegalStateException("No id field");
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@FunctionalInterface
	interface Filler {
		void fill(Workbook workbook) throws IOException;
	}

	private static byte[] build(Filler cb) throws IOException {
		try (Workbook wb = new XSSFWorkbook();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			cb.fill(wb);
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

	private static void row(Sheet sheet, int rowIdx, String... values) {
		Row r = sheet.createRow(rowIdx);
		for (int i = 0; i < values.length; i++) {
			r.createCell(i).setCellValue(values[i]);
		}
	}
}
