package com.edushift.modules.students.enrollments.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.students.enrollments.dto.CreateEnrollmentRequest;
import com.edushift.modules.students.enrollments.dto.EnrollmentResponse;
import com.edushift.modules.students.enrollments.dto.WithdrawEnrollmentRequest;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.mapper.StudentEnrollmentMapper;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for {@link StudentEnrollmentServiceImpl} (Sprint 4 — BE-4.8).
 *
 * <p>Each test exercises a single branch of the validation pipeline.
 * Repositories and the mapper are mocked / spied so the assertions
 * focus on the service contract: which 4xx/5xx code is raised, with
 * which message, and which arguments reach {@code saveAndFlush}.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StudentEnrollmentService — create + withdraw + listing")
class StudentEnrollmentServiceImplTest {

	@Mock private StudentEnrollmentRepository enrollmentRepository;
	@Mock private StudentRepository studentRepository;
	@Mock private SectionRepository sectionRepository;
	@Mock private AcademicYearRepository yearRepository;
	@Spy private StudentEnrollmentMapper mapper = new StudentEnrollmentMapper();

	@InjectMocks private StudentEnrollmentServiceImpl service;

	private Student student;
	private Section section;
	private AcademicYear year;

	@BeforeEach
	void setUp() {
		year = newYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
		section = newSection("A", year);
		student = newStudent("Ana", "Garcia", "12345678");
	}

	// =========================================================================
	// createEnrollment
	// =========================================================================

	@Nested
	@DisplayName("createEnrollment")
	class Create {

		@Test
		@DisplayName("happy path — saves and returns persisted projection")
		void happyPath() {
			stubAllLookups();
			when(enrollmentRepository.findActiveByStudentAndYear(student, year))
					.thenReturn(Optional.empty());
			when(enrollmentRepository.saveAndFlush(any(StudentEnrollment.class)))
					.thenAnswer(inv -> {
						StudentEnrollment e = inv.getArgument(0);
						setField(e, "id", UUID.randomUUID());
						e.setPublicUuid(UUID.randomUUID());
						setField(e, "createdAt", Instant.now());
						setField(e, "updatedAt", Instant.now());
						return e;
					});

			EnrollmentResponse response = service.createEnrollment(student.getPublicUuid(),
					new CreateEnrollmentRequest(section.getPublicUuid(),
							year.getPublicUuid(),
							LocalDate.of(2026, 3, 1),
							"   "));

			assertThat(response).isNotNull();
			assertThat(response.studentPublicUuid()).isEqualTo(student.getPublicUuid());
			assertThat(response.notes()).isNull();
			assertThat(response.status()).isEqualTo(StudentEnrollmentStatus.ACTIVE);
			ArgumentCaptor<StudentEnrollment> captor = ArgumentCaptor.forClass(StudentEnrollment.class);
			verify(enrollmentRepository).saveAndFlush(captor.capture());
			StudentEnrollment saved = captor.getValue();
			assertThat(saved.getStudent()).isSameAs(student);
			assertThat(saved.getSection()).isSameAs(section);
			assertThat(saved.getAcademicYear()).isSameAs(year);
			assertThat(saved.getEnrolledAt()).isEqualTo(LocalDate.of(2026, 3, 1));
			assertThat(saved.getStatus()).isEqualTo(StudentEnrollmentStatus.ACTIVE);
			assertThat(saved.getWithdrawnAt()).isNull();
		}

		@Test
		@DisplayName("student missing → 404 RESOURCE_NOT_FOUND (Student)")
		void studentNotFound() {
			when(studentRepository.findByPublicUuid(eq(student.getPublicUuid())))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.createEnrollment(student.getPublicUuid(),
					new CreateEnrollmentRequest(section.getPublicUuid(),
							year.getPublicUuid(),
							LocalDate.of(2026, 3, 1), null)))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessageContaining("Student");
			verify(enrollmentRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("section missing → 404 RESOURCE_NOT_FOUND (Section)")
		void sectionNotFound() {
			when(studentRepository.findByPublicUuid(eq(student.getPublicUuid())))
					.thenReturn(Optional.of(student));
			when(sectionRepository.findByPublicUuid(eq(section.getPublicUuid())))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.createEnrollment(student.getPublicUuid(),
					new CreateEnrollmentRequest(section.getPublicUuid(),
							year.getPublicUuid(),
							LocalDate.of(2026, 3, 1), null)))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessageContaining("Section");
		}

		@Test
		@DisplayName("year missing → 404 RESOURCE_NOT_FOUND (AcademicYear)")
		void yearNotFound() {
			when(studentRepository.findByPublicUuid(eq(student.getPublicUuid())))
					.thenReturn(Optional.of(student));
			when(sectionRepository.findByPublicUuid(eq(section.getPublicUuid())))
					.thenReturn(Optional.of(section));
			when(yearRepository.findByPublicUuid(eq(year.getPublicUuid())))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.createEnrollment(student.getPublicUuid(),
					new CreateEnrollmentRequest(section.getPublicUuid(),
							year.getPublicUuid(),
							LocalDate.of(2026, 3, 1), null)))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessageContaining("AcademicYear");
		}

		@Test
		@DisplayName("section.year != request.year → 409 ENROLLMENT_YEAR_MISMATCH")
		void yearMismatch() {
			AcademicYear otherYear = newYear("2025",
					LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
			when(studentRepository.findByPublicUuid(eq(student.getPublicUuid())))
					.thenReturn(Optional.of(student));
			when(sectionRepository.findByPublicUuid(eq(section.getPublicUuid())))
					.thenReturn(Optional.of(section));
			when(yearRepository.findByPublicUuid(eq(otherYear.getPublicUuid())))
					.thenReturn(Optional.of(otherYear));

			assertThatThrownBy(() -> service.createEnrollment(student.getPublicUuid(),
					new CreateEnrollmentRequest(section.getPublicUuid(),
							otherYear.getPublicUuid(),
							LocalDate.of(2025, 3, 1), null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("Section belongs to year");
			verify(enrollmentRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("enrolledAt before year start → 409 ENROLLMENT_DATE_OUT_OF_YEAR")
		void enrolledAtBeforeYear() {
			stubAllLookups();

			assertThatThrownBy(() -> service.createEnrollment(student.getPublicUuid(),
					new CreateEnrollmentRequest(section.getPublicUuid(),
							year.getPublicUuid(),
							LocalDate.of(2025, 12, 31), null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("outside the academic year window");
			verify(enrollmentRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("enrolledAt after year end → 409 ENROLLMENT_DATE_OUT_OF_YEAR")
		void enrolledAtAfterYear() {
			stubAllLookups();

			assertThatThrownBy(() -> service.createEnrollment(student.getPublicUuid(),
					new CreateEnrollmentRequest(section.getPublicUuid(),
							year.getPublicUuid(),
							LocalDate.of(2027, 1, 1), null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("outside the academic year window");
		}

		@Test
		@DisplayName("active row already exists → 409 STUDENT_ALREADY_ENROLLED")
		void duplicateActive() {
			stubAllLookups();
			when(enrollmentRepository.findActiveByStudentAndYear(student, year))
					.thenReturn(Optional.of(new StudentEnrollment()));

			assertThatThrownBy(() -> service.createEnrollment(student.getPublicUuid(),
					new CreateEnrollmentRequest(section.getPublicUuid(),
							year.getPublicUuid(),
							LocalDate.of(2026, 3, 1), null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("already has an active enrollment");
			verify(enrollmentRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("DB race condition → 409 STUDENT_ALREADY_ENROLLED")
		void raceCondition() {
			stubAllLookups();
			when(enrollmentRepository.findActiveByStudentAndYear(student, year))
					.thenReturn(Optional.empty());
			when(enrollmentRepository.saveAndFlush(any(StudentEnrollment.class)))
					.thenThrow(new DataIntegrityViolationException(
							"violates uk_student_enrollments_active"));

			assertThatThrownBy(() -> service.createEnrollment(student.getPublicUuid(),
					new CreateEnrollmentRequest(section.getPublicUuid(),
							year.getPublicUuid(),
							LocalDate.of(2026, 3, 1), null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("already has an active enrollment");
		}
	}

	// =========================================================================
	// withdrawEnrollment
	// =========================================================================

	@Nested
	@DisplayName("withdrawEnrollment")
	class Withdraw {

		@Test
		@DisplayName("happy path — sets terminal status + withdrawnAt")
		void happyPath() {
			StudentEnrollment active = newActiveEnrollment();
			when(enrollmentRepository.findByPublicUuid(active.getPublicUuid()))
					.thenReturn(Optional.of(active));
			when(enrollmentRepository.saveAndFlush(active)).thenReturn(active);

			EnrollmentResponse response = service.withdrawEnrollment(active.getPublicUuid(),
					new WithdrawEnrollmentRequest(StudentEnrollmentStatus.TRANSFERRED,
							LocalDate.of(2026, 5, 15)));

			assertThat(active.getStatus()).isEqualTo(StudentEnrollmentStatus.TRANSFERRED);
			assertThat(active.getWithdrawnAt()).isEqualTo(LocalDate.of(2026, 5, 15));
			assertThat(response.status()).isEqualTo(StudentEnrollmentStatus.TRANSFERRED);
		}

		@Test
		@DisplayName("not found → 404 RESOURCE_NOT_FOUND")
		void notFound() {
			UUID unknown = UUID.randomUUID();
			when(enrollmentRepository.findByPublicUuid(unknown))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.withdrawEnrollment(unknown,
					new WithdrawEnrollmentRequest(StudentEnrollmentStatus.WITHDRAWN,
							LocalDate.now())))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("already terminal → idempotent no-op (no save)")
		void alreadyTerminal() {
			StudentEnrollment ended = newActiveEnrollment();
			ended.setStatus(StudentEnrollmentStatus.GRADUATED);
			ended.setWithdrawnAt(LocalDate.of(2026, 11, 1));
			when(enrollmentRepository.findByPublicUuid(ended.getPublicUuid()))
					.thenReturn(Optional.of(ended));

			EnrollmentResponse response = service.withdrawEnrollment(ended.getPublicUuid(),
					new WithdrawEnrollmentRequest(StudentEnrollmentStatus.WITHDRAWN,
							LocalDate.of(2026, 12, 1)));

			assertThat(response.status()).isEqualTo(StudentEnrollmentStatus.GRADUATED);
			verify(enrollmentRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("target ACTIVE → 400 INVALID_WITHDRAW_STATUS")
		void invalidWithdrawStatus() {
			StudentEnrollment active = newActiveEnrollment();
			when(enrollmentRepository.findByPublicUuid(active.getPublicUuid()))
					.thenReturn(Optional.of(active));

			assertThatThrownBy(() -> service.withdrawEnrollment(active.getPublicUuid(),
					new WithdrawEnrollmentRequest(StudentEnrollmentStatus.ACTIVE,
							LocalDate.of(2026, 5, 15))))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("WITHDRAWN, TRANSFERRED, GRADUATED");
			verify(enrollmentRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("withdrawnAt < enrolledAt → 400 VALIDATION_ERROR")
		void withdrawDateBeforeEnrolled() {
			StudentEnrollment active = newActiveEnrollment();
			active.setEnrolledAt(LocalDate.of(2026, 4, 1));
			when(enrollmentRepository.findByPublicUuid(active.getPublicUuid()))
					.thenReturn(Optional.of(active));

			assertThatThrownBy(() -> service.withdrawEnrollment(active.getPublicUuid(),
					new WithdrawEnrollmentRequest(StudentEnrollmentStatus.WITHDRAWN,
							LocalDate.of(2026, 3, 15))))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("cannot be earlier than enrolledAt");
		}
	}

	// =========================================================================
	// listForStudent / listRoster
	// =========================================================================

	@Test
	@DisplayName("listForStudent — full timeline ordered desc")
	void listForStudent() {
		when(studentRepository.findByPublicUuid(student.getPublicUuid()))
				.thenReturn(Optional.of(student));
		when(enrollmentRepository.findAllByStudent(student))
				.thenReturn(List.of(newActiveEnrollment()));

		assertThat(service.listForStudent(student.getPublicUuid())).hasSize(1);
	}

	@Test
	@DisplayName("listRoster — only active rows for the section")
	void listRoster() {
		when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
				.thenReturn(Optional.of(section));
		when(enrollmentRepository.findActiveBySection(section))
				.thenReturn(List.of(newActiveEnrollment()));

		assertThat(service.listRoster(section.getPublicUuid())).hasSize(1);
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private void stubAllLookups() {
		when(studentRepository.findByPublicUuid(eq(student.getPublicUuid())))
				.thenReturn(Optional.of(student));
		when(sectionRepository.findByPublicUuid(eq(section.getPublicUuid())))
				.thenReturn(Optional.of(section));
		when(yearRepository.findByPublicUuid(eq(year.getPublicUuid())))
				.thenReturn(Optional.of(year));
	}

	private StudentEnrollment newActiveEnrollment() {
		StudentEnrollment e = new StudentEnrollment();
		setField(e, "id", UUID.randomUUID());
		e.setPublicUuid(UUID.randomUUID());
		e.setStudent(student);
		e.setSection(section);
		e.setAcademicYear(year);
		e.setEnrolledAt(LocalDate.of(2026, 3, 1));
		e.setStatus(StudentEnrollmentStatus.ACTIVE);
		setField(e, "createdAt", Instant.now());
		setField(e, "updatedAt", Instant.now());
		return e;
	}

	private static AcademicYear newYear(String name, LocalDate start, LocalDate end) {
		AcademicYear y = new AcademicYear();
		setField(y, "id", UUID.randomUUID());
		setField(y, "publicUuid", UUID.randomUUID());
		y.setName(name);
		y.setStartDate(start);
		y.setEndDate(end);
		return y;
	}

	private static Section newSection(String name, AcademicYear year) {
		Section s = new Section();
		s.setName(name);
		s.setAcademicYear(year);
		s.setDisplayOrder(1);
		setField(s, "id", UUID.randomUUID());
		s.setPublicUuid(UUID.randomUUID());
		return s;
	}

	private static Student newStudent(String first, String last, String document) {
		Student s = new Student();
		s.setFirstName(first);
		s.setLastName(last);
		s.setDocumentType(DocumentType.DNI);
		s.setDocumentNumber(document);
		s.setPublicUuid(UUID.randomUUID());
		setField(s, "id", UUID.randomUUID());
		return s;
	}

	private static void setField(Object target, String name, Object value) {
		Class<?> cls = target.getClass();
		while (cls != null) {
			try {
				Field f = cls.getDeclaredField(name);
				f.setAccessible(true);
				f.set(target, value);
				return;
			}
			catch (NoSuchFieldException ignored) {
				cls = cls.getSuperclass();
			}
			catch (IllegalAccessException ex) {
				throw new RuntimeException(ex);
			}
		}
		throw new RuntimeException("Field not found: " + name);
	}
}
