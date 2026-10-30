package com.edushift.modules.teachers.assignments.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class LmsAssignmentBinderTest {

	@Mock TeacherAssignmentRepository teacherAssignmentRepository;
	@Mock TeacherRepository teacherRepository;
	@Mock CurrentUserProvider currentUserProvider;

	LmsAssignmentBinder binder;

	UUID assignmentUuid = UUID.randomUUID();
	UUID sectionId = UUID.randomUUID();
	UUID teacherUserId = UUID.randomUUID();
	UUID teacherPublicUuid = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		binder = new LmsAssignmentBinder(
				teacherAssignmentRepository, teacherRepository, currentUserProvider);
		SecurityContextHolder.clearContext();
	}

	@Test
	void requireForCreate_rejectsNullUuid() {
		Section section = section(sectionId);
		assertThatThrownBy(() -> binder.requireForCreate(null, section))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("required");
	}

	@Test
	void requireForCreate_rejectsMissingAssignment() {
		when(teacherAssignmentRepository.findByPublicUuid(assignmentUuid))
				.thenReturn(Optional.empty());
		assertThatThrownBy(() -> binder.requireForCreate(assignmentUuid, section(sectionId)))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void requireForCreate_rejectsSectionMismatch() {
		TeacherAssignment assignment = activeAssignment(section(UUID.randomUUID()));
		when(teacherAssignmentRepository.findByPublicUuid(assignmentUuid))
				.thenReturn(Optional.of(assignment));
		assertThatThrownBy(() -> binder.requireForCreate(assignmentUuid, section(sectionId)))
				.isInstanceOf(BadRequestException.class)
				.satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
						.isEqualTo("ASSIGNMENT_SECTION_MISMATCH"));
	}

	@Test
	void requireForCreate_allowsTenantAdminAnyAssignmentInSection() {
		asRole("TENANT_ADMIN");
		Section section = section(sectionId);
		TeacherAssignment assignment = activeAssignment(section);
		when(teacherAssignmentRepository.findByPublicUuid(assignmentUuid))
				.thenReturn(Optional.of(assignment));

		TeacherAssignment result = binder.requireForCreate(assignmentUuid, section);
		assertThat(result).isSameAs(assignment);
	}

	@Test
	void requireForCreate_allowsOwningTeacher() {
		asRole("TEACHER");
		when(currentUserProvider.currentUserId()).thenReturn(Optional.of(teacherUserId));
		Section section = section(sectionId);
		TeacherAssignment assignment = activeAssignment(section);
		when(teacherAssignmentRepository.findByPublicUuid(assignmentUuid))
				.thenReturn(Optional.of(assignment));

		assertThat(binder.requireForCreate(assignmentUuid, section)).isSameAs(assignment);
	}

	@Test
	void resolveTeacherFilterForList_scopesTeacherToSelf() {
		asRole("TEACHER");
		when(currentUserProvider.currentUserId()).thenReturn(Optional.of(teacherUserId));
		Teacher teacher = new Teacher();
		teacher.setPublicUuid(teacherPublicUuid);
		when(teacherRepository.findByUserId(teacherUserId)).thenReturn(Optional.of(teacher));

		assertThat(binder.resolveTeacherFilterForList(null)).isEqualTo(teacherPublicUuid);
	}

	@Test
	void resolveTeacherFilterForList_rejectsForeignTeacherFilter() {
		asRole("TEACHER");
		when(currentUserProvider.currentUserId()).thenReturn(Optional.of(teacherUserId));
		Teacher teacher = new Teacher();
		teacher.setPublicUuid(teacherPublicUuid);
		when(teacherRepository.findByUserId(teacherUserId)).thenReturn(Optional.of(teacher));

		UUID otherTeacher = UUID.randomUUID();
		assertThatThrownBy(() -> binder.resolveTeacherFilterForList(otherTeacher))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void requireByPublicUuid_allowsOwningTeacher() {
		asRole("TEACHER");
		when(currentUserProvider.currentUserId()).thenReturn(Optional.of(teacherUserId));
		TeacherAssignment assignment = activeAssignment(section(sectionId));
		when(teacherAssignmentRepository.findByPublicUuid(assignmentUuid))
				.thenReturn(Optional.of(assignment));

		assertThat(binder.requireByPublicUuid(assignmentUuid)).isSameAs(assignment);
	}

	@Test
	void projection_readsCourseAndPeriod() {
		TeacherAssignment assignment = activeAssignment(section(sectionId));
		Course course = new Course();
		course.setPublicUuid(UUID.randomUUID());
		course.setName("Matemática");
		assignment.setCourse(course);

		assertThat(LmsAssignmentProjection.courseName(assignment)).isEqualTo("Matemática");
		assertThat(LmsAssignmentProjection.publicUuid(assignment)).isEqualTo(assignmentUuid);
		assertThat(LmsAssignmentProjection.courseName(null)).isNull();
	}

	private TeacherAssignment activeAssignment(Section section) {
		Teacher teacher = new Teacher();
		teacher.setUserId(teacherUserId);
		TeacherAssignment a = new TeacherAssignment();
		a.setPublicUuid(assignmentUuid);
		a.setSection(section);
		a.setTeacher(teacher);
		a.setUnassignedAt(null);
		return a;
	}

	private static Section section(UUID id) {
		Section s = new Section();
		s.setId(id);
		s.setPublicUuid(UUID.randomUUID());
		return s;
	}

	private static void asRole(String role) {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(
						"user",
						"n/a",
						java.util.List.of(new SimpleGrantedAuthority(role))));
	}
}
