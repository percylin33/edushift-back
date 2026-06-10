package com.edushift.modules.evaluations.mapper;

import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.evaluations.dto.CreateEvaluationRequest;
import com.edushift.modules.evaluations.dto.EvaluationListItem;
import com.edushift.modules.evaluations.dto.EvaluationResponse;
import com.edushift.modules.evaluations.dto.EvaluationResponse.AssignmentRef;
import com.edushift.modules.evaluations.dto.UpdateEvaluationRequest;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for
 * {@link com.edushift.modules.evaluations.entity.Evaluation} (Sprint 5B
 * / BE-5B.1). Same convention as the rest of the codebase (no MapStruct).
 *
 * <p>The {@code gradeCount} is passed in explicitly so the service can
 * choose a bulk lookup (listing) vs. a per-row count (detail). Wiring
 * lives in
 * {@link com.edushift.modules.evaluations.service.EvaluationService}.</p>
 *
 * <h3>Assignment label</h3>
 * The {@link AssignmentRef#label} string is rendered by the FE on
 * breadcrumbs. We build it as
 * {@code "<course.code> · <section.name> · <teacher.lastName> · <period.label>"}
 * so the UI can show a meaningful hint without N+1 fetches.
 */
@Component
public class EvaluationMapper {

	public EvaluationResponse toResponse(Evaluation evaluation, long gradeCount) {
		return new EvaluationResponse(
				evaluation.getPublicUuid(),
				toAssignmentRef(evaluation.getTeacherAssignment()),
				evaluation.getUnit() != null ? evaluation.getUnit().getPublicUuid() : null,
				evaluation.getLearningSession() != null
						? evaluation.getLearningSession().getPublicUuid()
						: null,
				evaluation.getKind(),
				evaluation.getName(),
				evaluation.getDescription(),
				evaluation.getWeight(),
				evaluation.getScheduledDate(),
				evaluation.getDueDate(),
				evaluation.getScale(),
				evaluation.getStatus(),
				evaluation.getPublishedAt(),
				evaluation.getClosedAt(),
				evaluation.getIsActive(),
				gradeCount,
				evaluation.getCreatedAt(),
				evaluation.getUpdatedAt()
		);
	}

	public EvaluationListItem toListItem(Evaluation evaluation, long gradeCount) {
		return new EvaluationListItem(
				evaluation.getPublicUuid(),
				evaluation.getKind(),
				evaluation.getName(),
				evaluation.getWeight(),
				evaluation.getScheduledDate(),
				evaluation.getDueDate(),
				evaluation.getScale(),
				evaluation.getStatus(),
				gradeCount,
				evaluation.getIsActive(),
				evaluation.getCreatedAt(),
				evaluation.getUpdatedAt()
		);
	}

	public Evaluation fromCreate(CreateEvaluationRequest request, TeacherAssignment assignment) {
		Evaluation evaluation = new Evaluation();
		evaluation.setTeacherAssignment(assignment);
		evaluation.setKind(request.kind());
		evaluation.setName(request.name().trim());
		evaluation.setDescription(blankToNull(request.description()));
		evaluation.setWeight(request.weight());
		evaluation.setScheduledDate(request.scheduledDate());
		evaluation.setDueDate(request.dueDate());
		evaluation.setScale(request.scale());
		// unit / learningSession are wired in the service so the FK
		// coherence checks can run before the row is persisted.
		evaluation.setIsActive(request.isActive() == null ? Boolean.TRUE : request.isActive());
		evaluation.setStatus(com.edushift.modules.evaluations.entity.EvaluationStatus.DRAFT);
		return evaluation;
	}

	/**
	 * Applies a partial-merge patch. The service is responsible for
	 * enforcing the lifecycle editability matrix (DRAFT vs PUBLISHED
	 * vs CLOSED) before invoking this method.
	 */
	public void applyUpdate(UpdateEvaluationRequest patch, Evaluation evaluation) {
		if (patch.kind() != null) {
			evaluation.setKind(patch.kind());
		}
		if (patch.name() != null) {
			evaluation.setName(patch.name().trim());
		}
		if (patch.description() != null) {
			evaluation.setDescription(blankToNull(patch.description()));
		}
		if (patch.weight() != null) {
			evaluation.setWeight(patch.weight());
		}
		if (patch.scheduledDate() != null) {
			evaluation.setScheduledDate(patch.scheduledDate());
		}
		if (patch.dueDate() != null) {
			evaluation.setDueDate(patch.dueDate());
		}
		if (patch.scale() != null) {
			evaluation.setScale(patch.scale());
		}
		if (patch.isActive() != null) {
			evaluation.setIsActive(patch.isActive());
		}
		// unitPublicUuid / learningSessionPublicUuid are wired in the
		// service (it owns the FK coherence checks).
	}

	private static AssignmentRef toAssignmentRef(TeacherAssignment assignment) {
		if (assignment == null) return null;
		String courseCode = assignment.getCourse() != null
				? assignment.getCourse().getCode()
				: "?";
		String sectionName = assignment.getSection() != null
				? assignment.getSection().getName()
				: "?";
		String teacherLast = assignment.getTeacher() != null
				? assignment.getTeacher().getLastName()
				: "?";
		String periodLabel = assignment.getAcademicPeriod() != null
				? formatPeriod(assignment.getAcademicPeriod().getPeriodType(),
						assignment.getAcademicPeriod().getOrdinal(),
						assignment.getAcademicPeriod().getName())
				: "?";
		String label = "%s · %s · %s · %s".formatted(
				courseCode, sectionName, teacherLast, periodLabel);
		return new AssignmentRef(assignment.getPublicUuid(), label);
	}

	private static String formatPeriod(PeriodType type, Integer ordinal, String name) {
		if (type == null) return name != null ? name : "?";
		String prefix = switch (type) {
			case BIMESTRE   -> "B";
			case TRIMESTRE  -> "T";
			case ANUAL      -> "A";
		};
		return ordinal != null ? "%s%d".formatted(prefix, ordinal) : name;
	}

	private static String blankToNull(String value) {
		if (value == null) return null;
		return value.isBlank() ? null : value;
	}
}
