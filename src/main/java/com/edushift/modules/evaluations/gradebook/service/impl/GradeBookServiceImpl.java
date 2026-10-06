package com.edushift.modules.evaluations.gradebook.service.impl;

import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.gradebook.GradeAverageCalculator;
import com.edushift.modules.evaluations.gradebook.GradeBookActaExporter;
import com.edushift.modules.evaluations.gradebook.GradeAverageCalculator.GradeKey;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookCategoryEntry;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookCellEntry;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookEvaluationEntry;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookResponse;
import com.edushift.modules.evaluations.gradebook.dto.GradeBookStudentEntry;
import com.edushift.modules.evaluations.gradebook.service.GradeBookService;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.graderegister.dto.GradeCategoryResponse;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategoryColumnMode;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateResponse;
import com.edushift.modules.evaluations.graderegister.service.GradeRegisterColumnMaterializer;
import com.edushift.modules.evaluations.graderegister.service.GradeRegisterTemplateService;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.tenants.branding.CurrentTenantLogoLoader;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GradeBookServiceImpl implements GradeBookService {

	private final TeacherAssignmentRepository assignmentRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final EvaluationRepository evaluationRepository;
	private final GradeRecordRepository gradeRecordRepository;
	private final GradeRegisterTemplateService gradeRegisterTemplateService;
	private final GradeRegisterColumnMaterializer columnMaterializer;
	private final CurrentTenantLogoLoader tenantLogoLoader;

	@Override
	@Transactional
	public GradeBookResponse buildGradeBook(
			UUID teacherAssignmentPublicUuid,
			boolean includeWithdrawn,
			boolean includeTransferred
	) {
		TeacherAssignment assignment = assignmentRepository
				.findByPublicUuid(teacherAssignmentPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"TeacherAssignment", teacherAssignmentPublicUuid));

		columnMaterializer.ensureColumns(assignment);

		Set<StudentEnrollmentStatus> statuses = EnumSet.of(StudentEnrollmentStatus.ACTIVE);
		if (includeWithdrawn) {
			statuses.add(StudentEnrollmentStatus.WITHDRAWN);
		}
		if (includeTransferred) {
			statuses.add(StudentEnrollmentStatus.TRANSFERRED);
		}

		List<StudentEnrollment> enrollments = enrollmentRepository
				.findBySectionAndStatusIn(assignment.getSection(), statuses);
		List<Evaluation> evaluations = evaluationRepository.findAllByAssignment(assignment);
		List<GradeRecord> grades = gradeRecordRepository.findAllByAssignment(assignment);

		Map<GradeKey, GradeRecord> gradeByKey = new HashMap<>(grades.size());
		for (GradeRecord g : grades) {
			gradeByKey.put(
					new GradeKey(g.getEvaluation().getId(), g.getStudent().getId()),
					g);
		}

		Optional<GradeRegisterTemplateResponse> templateOpt =
				gradeRegisterTemplateService.resolveForAssignment(assignment);
		List<GradeBookCategoryEntry> categoryEntries = templateOpt
				.map(t -> t.categories().stream()
						.map(this::toCategoryEntry)
						.toList())
				.orElse(List.of());
		Map<UUID, GradeCategoryResponse> categoryByUuid = new HashMap<>();
		templateOpt.ifPresent(t -> {
			for (GradeCategoryResponse c : t.categories()) {
				categoryByUuid.put(c.publicUuid(), c);
			}
		});

		List<GradeBookEvaluationEntry> evalEntries = new ArrayList<>(evaluations.size());
		for (Evaluation e : evaluations) {
			var cat = e.getCategory();
			evalEntries.add(new GradeBookEvaluationEntry(
					e.getPublicUuid(),
					e.getName(),
					e.getKind(),
					e.getScale(),
					e.getStatus(),
					e.getWeight(),
					e.getScheduledDate(),
					cat != null ? cat.getPublicUuid() : null,
					cat != null ? cat.getName() : null));
		}

		boolean literalSheet = templateOpt
				.map(t -> t.gradingScale() == EvaluationScale.LITERAL_CNEB)
				.orElse(false);

		List<GradeBookStudentEntry> studentEntries = new ArrayList<>(enrollments.size());
		for (StudentEnrollment enrollment : enrollments) {
			Student student = enrollment.getStudent();
			String enrollmentStatus = enrollment.getStatus() != null
					? enrollment.getStatus().name() : "ACTIVE";
			GradeAverageCalculator.Row row = GradeAverageCalculator.forStudent(
					student.getId(),
					evaluations,
					gradeByKey,
					categoryEntries,
					literalSheet);
			studentEntries.add(new GradeBookStudentEntry(
					student.getPublicUuid(),
					fullName(student),
					row.weightedAverage(),
					enrollmentStatus,
					row.categoryAverages(),
					row.literalAverage(),
					row.categoryLiterals()));
		}

		List<GradeBookCellEntry> cellEntries = new ArrayList<>(grades.size());
		for (GradeRecord g : grades) {
			cellEntries.add(new GradeBookCellEntry(
					g.getStudent().getPublicUuid(),
					g.getEvaluation().getPublicUuid(),
					g.getScore(),
					g.getLiteral(),
					g.getRecordedAt()));
		}

		Teacher teacher = assignment.getTeacher();
		AcademicPeriod period = assignment.getAcademicPeriod();
		var section = assignment.getSection();
		var grade = section.getGrade();
		var level = grade != null ? grade.getLevel() : null;

		log.info("[gradebook] built -- assignment={} students={} evaluations={} cells={}",
				assignment.getPublicUuid(),
				studentEntries.size(),
				evalEntries.size(),
				cellEntries.size());

		return new GradeBookResponse(
				assignment.getPublicUuid(),
				section.getPublicUuid(),
				section.getName(),
				grade != null ? grade.getPublicUuid() : null,
				grade != null ? grade.getName() : null,
				grade != null ? grade.getOrdinal() : null,
				level != null ? level.getCode() : null,
				assignment.getCourse().getPublicUuid(),
				assignment.getCourse().getName(),
				teacher.fullName(),
				period.getPublicUuid(),
				period.getPeriodType(),
				period.getOrdinal(),
				period.getName(),
				templateOpt.map(GradeRegisterTemplateResponse::publicUuid).orElse(null),
				templateOpt.map(GradeRegisterTemplateResponse::name).orElse(null),
				templateOpt.map(GradeRegisterTemplateResponse::gradingScale).orElse(null),
				categoryEntries,
				studentEntries,
				evalEntries,
				cellEntries);
	}

	@Override
	@Transactional(readOnly = true)
	public byte[] exportXlsx(
			UUID teacherAssignmentPublicUuid,
			boolean includeWithdrawn,
			boolean includeTransferred
	) {
		GradeBookResponse gb = buildGradeBook(
				teacherAssignmentPublicUuid, includeWithdrawn, includeTransferred);
		return GradeBookActaExporter.toXlsx(gb, academicYearName(teacherAssignmentPublicUuid));
	}

	@Override
	@Transactional(readOnly = true)
	public byte[] exportPdf(
			UUID teacherAssignmentPublicUuid,
			boolean includeWithdrawn,
			boolean includeTransferred
	) {
		GradeBookResponse gb = buildGradeBook(
				teacherAssignmentPublicUuid, includeWithdrawn, includeTransferred);
		return GradeBookActaExporter.toPdf(
				gb,
				academicYearName(teacherAssignmentPublicUuid),
				tenantLogoLoader.schoolLogoOrNull());
	}

	private String academicYearName(UUID teacherAssignmentPublicUuid) {
		return assignmentRepository.findByPublicUuid(teacherAssignmentPublicUuid)
				.map(TeacherAssignment::getAcademicPeriod)
				.map(AcademicPeriod::getAcademicYear)
				.map(AcademicYear::getName)
				.orElse("");
	}

	private GradeBookCategoryEntry toCategoryEntry(GradeCategoryResponse c) {
		return new GradeBookCategoryEntry(
				c.publicUuid(),
				c.name(),
				c.colorToken(),
				c.sortOrder(),
				c.weight(),
				c.contributesToAverage(),
				c.allowsTeacherColumns() || c.columnMode() == GradeCategoryColumnMode.OPEN);
	}

	private static String fullName(Student student) {
		return student.listDisplayName();
	}

}
