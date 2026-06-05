package com.edushift.modules.students.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.students.dto.CreateStudentRequest;
import com.edushift.modules.students.dto.StudentListFilters;
import com.edushift.modules.students.dto.StudentListItem;
import com.edushift.modules.students.dto.StudentResponse;
import com.edushift.modules.students.dto.UpdateStudentRequest;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.mapper.StudentMapper;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class StudentServiceImplTest {

	@Mock private StudentRepository studentRepository;
	@Spy private StudentMapper mapper = new StudentMapper();

	@InjectMocks private StudentServiceImpl service;

	// ===========================================================================
	// listStudents
	// ===========================================================================

	@Nested
	@DisplayName("listStudents")
	class ListStudents {

		@Test
		@DisplayName("delegates to repo with a Specification + maps each row to a list item")
		void delegatesAndMaps() {
			Student a = newStudent("12345678");
			Student b = newStudent("87654321");
			when(studentRepository.findAll(any(Specification.class), any(Pageable.class)))
					.thenReturn(new PageImpl<>(List.of(a, b)));

			Page<StudentListItem> page = service.listStudents(StudentListFilters.empty(), Pageable.unpaged());

			assertThat(page.getContent()).hasSize(2);
			assertThat(page.getContent().get(0).documentNumber()).isEqualTo("12345678");
		}

		@Test
		@DisplayName("null filters are treated as empty (no NPE downstream)")
		void nullFiltersAreSafe() {
			when(studentRepository.findAll(any(Specification.class), any(Pageable.class)))
					.thenReturn(new PageImpl<>(List.of()));

			service.listStudents(null, Pageable.unpaged());

			verify(studentRepository).findAll(any(Specification.class), any(Pageable.class));
		}

		@Test
		@DisplayName("gradeLevelId filter is accepted but ignored (Sprint 4 land)")
		void gradeLevelIdIgnoredForNow() {
			when(studentRepository.findAll(any(Specification.class), any(Pageable.class)))
					.thenReturn(new PageImpl<>(List.of()));

			service.listStudents(
					new StudentListFilters(null, null, "any-grade"),
					Pageable.unpaged());

			verify(studentRepository).findAll(any(Specification.class), any(Pageable.class));
		}
	}

	// ===========================================================================
	// getStudent
	// ===========================================================================

	@Nested
	@DisplayName("getStudent")
	class GetStudent {

		@Test
		@DisplayName("happy path — returns full projection")
		void happyPath() {
			Student s = newStudent("12345678");
			when(studentRepository.findByPublicUuid(s.getPublicUuid())).thenReturn(Optional.of(s));

			StudentResponse response = service.getStudent(s.getPublicUuid());

			assertThat(response.documentNumber()).isEqualTo("12345678");
		}

		@Test
		@DisplayName("unknown publicUuid → ResourceNotFoundException")
		void unknownThrows() {
			UUID id = UUID.randomUUID();
			when(studentRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.getStudent(id))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// ===========================================================================
	// createStudent
	// ===========================================================================

	@Nested
	@DisplayName("createStudent")
	class CreateStudent {

		@Test
		@DisplayName("happy path — pre-checks pass, persists, returns full projection")
		void happyPath() {
			when(studentRepository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "12345678"))
					.thenReturn(Optional.empty());
			when(studentRepository.findByEmailIgnoreCase("ada@acme.test"))
					.thenReturn(Optional.empty());
			when(studentRepository.saveAndFlush(any(Student.class)))
					.thenAnswer(inv -> {
						Student s = inv.getArgument(0);
						setIdViaReflection(s, UUID.randomUUID());
						s.setPublicUuid(UUID.randomUUID());
						return s;
					});

			CreateStudentRequest request = new CreateStudentRequest(
					DocumentType.DNI, "12345678",
					"Ada", "Lovelace", "Byron",
					null, null,
					"ada@acme.test", null, null,
					EnrollmentStatus.ENROLLED, null, null);

			StudentResponse response = service.createStudent(request);

			assertThat(response.email()).isEqualTo("ada@acme.test");
			assertThat(response.fullName()).isEqualTo("Ada Lovelace Byron");
		}

		@Test
		@DisplayName("duplicate document → ConflictException(STUDENT_DOCUMENT_TAKEN), no save")
		void duplicateDocumentRejected() {
			Student existing = newStudent("12345678");
			when(studentRepository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "12345678"))
					.thenReturn(Optional.of(existing));

			CreateStudentRequest request = new CreateStudentRequest(
					DocumentType.DNI, "12345678",
					"Ada", "Lovelace", null,
					null, null,
					null, null, null,
					null, null, null);

			assertThatThrownBy(() -> service.createStudent(request))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("STUDENT_DOCUMENT_TAKEN"));

			verify(studentRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("duplicate email → ConflictException(STUDENT_EMAIL_TAKEN), no save")
		void duplicateEmailRejected() {
			when(studentRepository.findByDocumentTypeAndDocumentNumber(any(), any()))
					.thenReturn(Optional.empty());
			Student existing = newStudent("99999999");
			existing.setEmail("ada@acme.test");
			when(studentRepository.findByEmailIgnoreCase("ada@acme.test"))
					.thenReturn(Optional.of(existing));

			CreateStudentRequest request = new CreateStudentRequest(
					DocumentType.DNI, "12345678",
					"Ada", "Lovelace", null,
					null, null,
					"ada@acme.test", null, null,
					null, null, null);

			assertThatThrownBy(() -> service.createStudent(request))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("STUDENT_EMAIL_TAKEN"));

			verify(studentRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("race condition: pre-check passes but INSERT trips unique → translates to STUDENT_DOCUMENT_TAKEN")
		void racingDocumentInsertSurfacesAsConflict() {
			when(studentRepository.findByDocumentTypeAndDocumentNumber(any(), any()))
					.thenReturn(Optional.empty());
			// Email is null in the request below, so findByEmailIgnoreCase
			// is never called — no stub for it.
			when(studentRepository.saveAndFlush(any(Student.class)))
					.thenThrow(new DataIntegrityViolationException(
							"could not execute statement [ERROR: duplicate key value violates unique constraint "
									+ "\"uk_students_tenant_document_active\"]"));

			CreateStudentRequest request = new CreateStudentRequest(
					DocumentType.DNI, "12345678",
					"Ada", "Lovelace", null,
					null, null,
					null, null, null,
					null, null, null);

			assertThatThrownBy(() -> service.createStudent(request))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("STUDENT_DOCUMENT_TAKEN"));
		}
	}

	// ===========================================================================
	// updateStudent
	// ===========================================================================

	@Nested
	@DisplayName("updateStudent")
	class UpdateStudent {

		@Test
		@DisplayName("non-empty patch — applies and persists")
		void appliesPatch() {
			Student s = newStudent("12345678");
			when(studentRepository.findByPublicUuid(s.getPublicUuid())).thenReturn(Optional.of(s));
			when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

			UpdateStudentRequest patch = new UpdateStudentRequest(
					null, null, "Augusta", null, null, null, null,
					null, null, null,
					EnrollmentStatus.GRADUATED, null, null);

			StudentResponse response = service.updateStudent(s.getPublicUuid(), patch);

			assertThat(response.firstName()).isEqualTo("Augusta");
			assertThat(response.enrollmentStatus()).isEqualTo(EnrollmentStatus.GRADUATED);
		}

		@Test
		@DisplayName("empty patch short-circuits — no save")
		void emptyPatchSkipsSave() {
			Student s = newStudent("12345678");
			when(studentRepository.findByPublicUuid(s.getPublicUuid())).thenReturn(Optional.of(s));

			service.updateStudent(s.getPublicUuid(), new UpdateStudentRequest(
					null, null, null, null, null, null, null,
					null, null, null, null, null, null));

			verify(studentRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("changing documentNumber to one taken by another student → STUDENT_DOCUMENT_TAKEN")
		void documentChangeCollides() {
			Student me = newStudent("12345678");
			Student other = newStudent("99999999");
			when(studentRepository.findByPublicUuid(me.getPublicUuid())).thenReturn(Optional.of(me));
			when(studentRepository.findByDocumentTypeAndDocumentNumber(DocumentType.DNI, "99999999"))
					.thenReturn(Optional.of(other));

			UpdateStudentRequest patch = new UpdateStudentRequest(
					null, "99999999", null, null, null, null, null,
					null, null, null, null, null, null);

			assertThatThrownBy(() -> service.updateStudent(me.getPublicUuid(), patch))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("STUDENT_DOCUMENT_TAKEN"));
		}

		@Test
		@DisplayName("patching to the SAME documentNumber the entity already has → no false positive")
		void sameDocumentDoesNotConflict() {
			Student me = newStudent("12345678");
			when(studentRepository.findByPublicUuid(me.getPublicUuid())).thenReturn(Optional.of(me));
			when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

			UpdateStudentRequest patch = new UpdateStudentRequest(
					DocumentType.DNI, "12345678", "Augusta", null, null, null, null,
					null, null, null, null, null, null);

			service.updateStudent(me.getPublicUuid(), patch);

			// findByDocumentTypeAndDocumentNumber should NOT have been
			// called because the values didn't change.
			verify(studentRepository, never())
					.findByDocumentTypeAndDocumentNumber(any(), any());
		}
	}

	// ===========================================================================
	// deleteStudent
	// ===========================================================================

	@Nested
	@DisplayName("deleteStudent")
	class DeleteStudent {

		@Test
		@DisplayName("happy path — soft-deletes via repository.delete")
		void happyPath() {
			Student s = newStudent("12345678");
			when(studentRepository.findByPublicUuid(s.getPublicUuid())).thenReturn(Optional.of(s));

			service.deleteStudent(s.getPublicUuid());

			verify(studentRepository).delete(s);
		}

		@Test
		@DisplayName("unknown publicUuid → ResourceNotFoundException, no delete")
		void unknownThrows() {
			UUID id = UUID.randomUUID();
			when(studentRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.deleteStudent(id))
					.isInstanceOf(ResourceNotFoundException.class);

			verify(studentRepository, never()).delete(any(Student.class));
		}
	}

	// ===========================================================================
	// Fixtures
	// ===========================================================================

	private static Student newStudent(String documentNumber) {
		Student s = new Student();
		setIdViaReflection(s, UUID.randomUUID());
		s.setPublicUuid(UUID.randomUUID());
		s.setDocumentType(DocumentType.DNI);
		s.setDocumentNumber(documentNumber);
		s.setFirstName("Ada");
		s.setLastName("Lovelace");
		s.setSecondLastName("Byron");
		s.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
		s.setMetadata(new java.util.HashMap<>());
		return s;
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
			throw new IllegalStateException("No 'id' field");
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
