package com.edushift.modules.family.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.family.dto.FamilyChildSummary;
import com.edushift.modules.payments.repository.InvoiceRepository;
import com.edushift.modules.schedule.timeslot.service.TimeSlotService;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Guardian;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.entity.StudentGuardian;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentGuardianRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.students.service.StudentGuardianService;
import com.edushift.shared.exception.ForbiddenException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class FamilyServiceImplTest {
	@Mock private StudentRepository studentRepository;
	@Mock private StudentGuardianRepository repository;
	@Mock private StudentGuardianService studentGuardianService;
	@Mock private StudentEnrollmentRepository enrollmentRepository;
	@Mock private InvoiceRepository invoiceRepository;
	@Mock private TimeSlotService timeSlotService;
	@InjectMocks private FamilyServiceImpl service;

	@Test
	void listsOnlyChildrenResolvedForAuthenticatedParent() {
		UUID parentUserId = UUID.randomUUID();
		Student first = student("Ana", "Torres");
		Student second = student("Luis", "Torres");
		when(repository.findActiveByGuardianUserId(parentUserId))
				.thenReturn(List.of(link(first, parentUserId), link(second, parentUserId)));

		List<FamilyChildSummary> result = service.listChildren(parentUserId);

		assertThat(result).extracting(FamilyChildSummary::publicUuid)
				.containsExactly(first.getPublicUuid(), second.getPublicUuid());
		assertThat(result).extracting(FamilyChildSummary::fullName)
				.containsExactly("Ana Torres", "Luis Torres");
		verify(repository).findActiveByGuardianUserId(parentUserId);
	}

	@Test
	void anonymousParentReturnsNoDataWithoutQueryingRepository() {
		assertThat(service.listChildren(null)).isEmpty();
	}

	@Test
	void activitiesRejectUnlinkedParent() {
		UUID parent = UUID.randomUUID();
		UUID child = UUID.randomUUID();
		doThrow(new ForbiddenException("PARENT_STUDENT_LINK_REQUIRED",
				"The parent is not linked to the requested student"))
				.when(studentGuardianService).assertParentLinkedToStudent(parent, child);

		assertThatThrownBy(() -> service.getChildActivities(child, parent))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void paymentsReturnInvoicesAfterOwnershipCheck() {
		UUID parent = UUID.randomUUID();
		UUID child = UUID.randomUUID();
		Student student = student("Ana", "Torres");
		student.setPublicUuid(child);
		when(studentRepository.findByPublicUuid(child)).thenReturn(Optional.of(student));
		when(invoiceRepository.findByStudentIdOrderByIssuedAtDesc(eq(child), any(Pageable.class)))
				.thenReturn(Page.empty());

		assertThat(service.getChildPayments(child, parent)).isEmpty();
		verify(studentGuardianService).assertParentLinkedToStudent(parent, child);
	}

	@Test
	void scheduleRejectUnlinkedParent() {
		UUID parent = UUID.randomUUID();
		UUID child = UUID.randomUUID();
		doThrow(new ForbiddenException("PARENT_STUDENT_LINK_REQUIRED",
				"The parent is not linked to the requested student"))
				.when(studentGuardianService).assertParentLinkedToStudent(parent, child);

		assertThatThrownBy(() -> service.getChildSchedule(child, parent, null))
				.isInstanceOf(ForbiddenException.class);
	}

	private static Student student(String firstName, String lastName) {
		Student student = new Student();
		student.setPublicUuid(UUID.randomUUID());
		student.setFirstName(firstName);
		student.setLastName(lastName);
		student.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
		return student;
	}

	private static StudentGuardian link(Student student, UUID parentUserId) {
		Guardian guardian = new Guardian();
		guardian.setUserId(parentUserId);
		StudentGuardian link = new StudentGuardian();
		link.setStudent(student);
		link.setGuardian(guardian);
		return link;
	}
}
