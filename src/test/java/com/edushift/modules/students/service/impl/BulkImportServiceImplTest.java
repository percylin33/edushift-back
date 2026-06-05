package com.edushift.modules.students.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.students.dto.BulkImportJobResponse;
import com.edushift.modules.students.entity.BulkImportJob;
import com.edushift.modules.students.entity.BulkImportStatus;
import com.edushift.modules.students.mapper.BulkImportJobMapper;
import com.edushift.modules.students.repository.BulkImportJobRepository;
import com.edushift.modules.students.service.bulk.StudentBulkImportRunner;
import com.edushift.modules.students.service.bulk.StudentTemplateGenerator;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class BulkImportServiceImplTest {

	@Mock private BulkImportJobRepository jobRepository;
	@Spy private BulkImportJobMapper mapper = new BulkImportJobMapper();
	@Mock private StudentBulkImportRunner runner;
	@Mock private StudentTemplateGenerator templateGenerator;

	@InjectMocks private BulkImportServiceImpl service;

	private static final UUID TENANT_ID = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		TenantContext.set(TENANT_ID);
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	// ===========================================================================
	// enqueueStudentsImport
	// ===========================================================================

	@Nested
	@DisplayName("enqueueStudentsImport")
	class Enqueue {

		@Test
		@DisplayName("happy path — creates PENDING job, dispatches the runner, returns the handle")
		void happyPath() {
			MockMultipartFile file = new MockMultipartFile(
					"file", "students.xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					"payload-bytes".getBytes());

			when(jobRepository.saveAndFlush(any(BulkImportJob.class))).thenAnswer(inv -> {
				BulkImportJob j = inv.getArgument(0);
				setIdViaReflection(j, UUID.randomUUID());
				j.setPublicUuid(UUID.randomUUID());
				return j;
			});

			BulkImportJobResponse response = service.enqueueStudentsImport(file);

			ArgumentCaptor<BulkImportJob> captor = ArgumentCaptor.forClass(BulkImportJob.class);
			verify(jobRepository).saveAndFlush(captor.capture());
			BulkImportJob saved = captor.getValue();
			assertThat(saved.getStatus()).isEqualTo(BulkImportStatus.PENDING);
			assertThat(saved.getFileName()).isEqualTo("students.xlsx");
			assertThat(saved.getFileSizeBytes()).isEqualTo("payload-bytes".getBytes().length);

			verify(runner).run(eq(saved.getId()), eq(TENANT_ID), any(byte[].class));
			assertThat(response.fileName()).isEqualTo("students.xlsx");
		}

		@Test
		@DisplayName("non-xlsx extension → BusinessException(UNSUPPORTED_FILE_TYPE)")
		void wrongExtension() {
			MockMultipartFile file = new MockMultipartFile(
					"file", "students.csv", "text/csv", "x".getBytes());

			assertThatThrownBy(() -> service.enqueueStudentsImport(file))
					.isInstanceOfSatisfying(BusinessException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("UNSUPPORTED_FILE_TYPE"));

			verify(jobRepository, never()).saveAndFlush(any(BulkImportJob.class));
			verify(runner, never()).run(any(), any(), any());
		}

		@Test
		@DisplayName("empty upload → BusinessException(INVALID_FILE)")
		void emptyUpload() {
			MockMultipartFile file = new MockMultipartFile(
					"file", "students.xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					new byte[0]);

			assertThatThrownBy(() -> service.enqueueStudentsImport(file))
					.isInstanceOfSatisfying(BusinessException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("INVALID_FILE"));
		}

		@Test
		@DisplayName("null path component is stripped from filename")
		void filenameSanitized() {
			MockMultipartFile file = new MockMultipartFile(
					"file", "..\\..\\evil/students.xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					"x".getBytes());

			when(jobRepository.saveAndFlush(any(BulkImportJob.class))).thenAnswer(inv -> {
				BulkImportJob j = inv.getArgument(0);
				setIdViaReflection(j, UUID.randomUUID());
				j.setPublicUuid(UUID.randomUUID());
				return j;
			});

			service.enqueueStudentsImport(file);

			ArgumentCaptor<BulkImportJob> captor = ArgumentCaptor.forClass(BulkImportJob.class);
			verify(jobRepository).saveAndFlush(captor.capture());
			assertThat(captor.getValue().getFileName()).isEqualTo("students.xlsx");
		}
	}

	// ===========================================================================
	// getJob
	// ===========================================================================

	@Nested
	@DisplayName("getJob")
	class GetJob {

		@Test
		@DisplayName("happy path — returns the mapped DTO")
		void happyPath() {
			BulkImportJob job = new BulkImportJob();
			setIdViaReflection(job, UUID.randomUUID());
			UUID publicUuid = UUID.randomUUID();
			job.setPublicUuid(publicUuid);
			job.setJobType(com.edushift.modules.students.entity.BulkImportJobType.STUDENTS);
			job.setFileName("u.xlsx");
			job.setFileSizeBytes(1);

			when(jobRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(job));

			BulkImportJobResponse response = service.getJob(publicUuid);

			assertThat(response.publicUuid()).isEqualTo(publicUuid);
		}

		@Test
		@DisplayName("unknown publicUuid → ResourceNotFoundException")
		void unknown() {
			UUID id = UUID.randomUUID();
			when(jobRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.getJob(id))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// ===========================================================================
	// generateStudentsTemplate
	// ===========================================================================

	@Test
	@DisplayName("generateStudentsTemplate delegates to the generator verbatim")
	void delegatesToGenerator() {
		byte[] expected = new byte[] {1, 2, 3};
		when(templateGenerator.generate()).thenReturn(expected);

		assertThat(service.generateStudentsTemplate()).isEqualTo(expected);
	}

	// ---------------------------------------------------------------------------
	// Reflection helpers
	// ---------------------------------------------------------------------------

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
}
