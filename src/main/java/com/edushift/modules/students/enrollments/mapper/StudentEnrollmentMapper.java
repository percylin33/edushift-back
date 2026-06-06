package com.edushift.modules.students.enrollments.mapper;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.students.enrollments.dto.EnrollmentListItem;
import com.edushift.modules.students.enrollments.dto.EnrollmentResponse;
import com.edushift.modules.students.enrollments.dto.SectionStudentRosterItem;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.entity.Student;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link StudentEnrollment}.
 *
 * <p>Each accessor traverses a lazy association
 * ({@code student.firstName}, {@code section.name}, ...); we resolve
 * them all at projection time so the response is fully materialised
 * when it leaves the {@code @Transactional} boundary. List endpoints
 * batch-load by definition because the JPQL already orders by
 * associated columns.</p>
 */
@Component
public class StudentEnrollmentMapper {

	public EnrollmentResponse toResponse(StudentEnrollment e) {
		Student student = e.getStudent();
		Section section = e.getSection();
		AcademicYear year = e.getAcademicYear();
		return new EnrollmentResponse(
				e.getPublicUuid(),
				student.getPublicUuid(),
				student.fullName(),
				student.getDocumentNumber(),
				section.getPublicUuid(),
				section.getName(),
				year.getPublicUuid(),
				year.getName(),
				e.getEnrolledAt(),
				e.getWithdrawnAt(),
				e.getStatus(),
				e.isActive(),
				e.getNotes(),
				e.getCreatedAt(),
				e.getUpdatedAt()
		);
	}

	public EnrollmentListItem toListItem(StudentEnrollment e) {
		Student student = e.getStudent();
		Section section = e.getSection();
		AcademicYear year = e.getAcademicYear();
		return new EnrollmentListItem(
				e.getPublicUuid(),
				student.getPublicUuid(),
				student.fullName(),
				section.getPublicUuid(),
				section.getName(),
				year.getPublicUuid(),
				year.getName(),
				e.getEnrolledAt(),
				e.getWithdrawnAt(),
				e.getStatus(),
				e.isActive()
		);
	}

	public SectionStudentRosterItem toRosterItem(StudentEnrollment e) {
		Student student = e.getStudent();
		return new SectionStudentRosterItem(
				e.getPublicUuid(),
				student.getPublicUuid(),
				student.fullName(),
				student.getDocumentType(),
				student.getDocumentNumber(),
				student.getEmail(),
				e.getEnrolledAt(),
				e.getWithdrawnAt(),
				e.getStatus(),
				e.isActive()
		);
	}
}
