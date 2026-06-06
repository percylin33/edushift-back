package com.edushift.modules.teachers.assignments.mapper;

import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.teachers.assignments.dto.AssignmentListItem;
import com.edushift.modules.teachers.assignments.dto.AssignmentResponse;
import com.edushift.modules.teachers.assignments.dto.SectionTeacherItem;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.entity.Teacher;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link TeacherAssignment}.
 *
 * <p>Each accessor on {@code TeacherAssignment} traverses a lazy
 * association ({@code teacher.firstName}, {@code section.name}, ...);
 * we deliberately resolve them all at projection time so the response
 * is fully materialised when it leaves the {@code @Transactional}
 * boundary. List endpoints batch-load by definition because the JPQL
 * already orders by associated columns.</p>
 */
@Component
public class TeacherAssignmentMapper {

	public AssignmentResponse toResponse(TeacherAssignment a) {
		AcademicPeriod period = a.getAcademicPeriod();
		Section section = a.getSection();
		Teacher teacher = a.getTeacher();
		return new AssignmentResponse(
				a.getPublicUuid(),
				teacher.getPublicUuid(),
				teacher.fullName(),
				section.getPublicUuid(),
				section.getName(),
				a.getCourse().getPublicUuid(),
				a.getCourse().getCode(),
				a.getCourse().getName(),
				period.getPublicUuid(),
				period.getPeriodType(),
				period.getOrdinal(),
				period.getName(),
				period.getAcademicYear().getPublicUuid(),
				period.getAcademicYear().getName(),
				a.getAssignedAt(),
				a.getUnassignedAt(),
				a.isActive(),
				a.getNotes(),
				a.getCreatedAt(),
				a.getUpdatedAt()
		);
	}

	public AssignmentListItem toListItem(TeacherAssignment a) {
		AcademicPeriod period = a.getAcademicPeriod();
		return new AssignmentListItem(
				a.getPublicUuid(),
				a.getTeacher().getPublicUuid(),
				a.getTeacher().fullName(),
				a.getSection().getPublicUuid(),
				a.getSection().getName(),
				a.getCourse().getPublicUuid(),
				a.getCourse().getCode(),
				a.getCourse().getName(),
				period.getPublicUuid(),
				period.getPeriodType(),
				period.getOrdinal(),
				a.getAssignedAt(),
				a.getUnassignedAt(),
				a.isActive()
		);
	}

	public SectionTeacherItem toSectionTeacherItem(TeacherAssignment a) {
		Teacher teacher = a.getTeacher();
		AcademicPeriod period = a.getAcademicPeriod();
		return new SectionTeacherItem(
				a.getPublicUuid(),
				teacher.getPublicUuid(),
				teacher.fullName(),
				teacher.getEmail(),
				a.getCourse().getPublicUuid(),
				a.getCourse().getCode(),
				a.getCourse().getName(),
				period.getPublicUuid(),
				period.getPeriodType(),
				period.getOrdinal(),
				a.getAssignedAt()
		);
	}
}
