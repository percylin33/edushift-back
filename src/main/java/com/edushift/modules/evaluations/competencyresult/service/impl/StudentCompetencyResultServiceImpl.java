package com.edushift.modules.evaluations.competencyresult.service.impl;

import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.evaluations.competencyresult.dto.CriterionResultInput;
import com.edushift.modules.evaluations.competencyresult.dto.StudentCompetencyResultInput;
import com.edushift.modules.evaluations.competencyresult.dto.StudentCompetencyResultResponse;
import com.edushift.modules.evaluations.competencyresult.dto.StudentCompetencyResultRevisionResponse;
import com.edushift.modules.evaluations.competencyresult.dto.UpsertStudentCompetencyResultsRequest;
import com.edushift.modules.evaluations.competencyresult.entity.StudentCompetencyResult;
import com.edushift.modules.evaluations.competencyresult.entity.StudentCompetencyResultRevision;
import com.edushift.modules.evaluations.competencyresult.repository.StudentCompetencyResultRepository;
import com.edushift.modules.evaluations.competencyresult.repository.StudentCompetencyResultRevisionRepository;
import com.edushift.modules.evaluations.competencyresult.service.StudentCompetencyResultService;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@RequiredArgsConstructor
public class StudentCompetencyResultServiceImpl implements StudentCompetencyResultService {

	private static final String RESOURCE = "StudentCompetencyResult";
	private final StudentCompetencyResultRepository resultRepository;
	private final StudentCompetencyResultRevisionRepository revisionRepository;
	private final EvaluationRepository evaluationRepository;
	private final StudentRepository studentRepository;
	private final CompetencyRepository competencyRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final CurrentUserProvider currentUserProvider;
	private final AuditLogger auditLogger;

	@Override
	@Transactional(readOnly = true)
	public List<StudentCompetencyResultResponse> list(UUID evaluationPublicUuid, UUID studentPublicUuid) {
		Evaluation evaluation = loadEvaluation(evaluationPublicUuid);
		ensureEvaluationAccess(evaluation);
		Student student = loadStudent(studentPublicUuid);
		ensureStudentInEvaluationSection(evaluation, student);
		return resultRepository.findActive(evaluation, student).stream().map(this::toResponse).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<StudentCompetencyResultRevisionResponse> history(UUID evaluationPublicUuid,
			UUID studentPublicUuid, UUID competencyPublicUuid) {
		Evaluation evaluation = loadEvaluation(evaluationPublicUuid);
		ensureEvaluationAccess(evaluation);
		Student student = loadStudent(studentPublicUuid);
		ensureStudentInEvaluationSection(evaluation, student);
		Competency competency = competencyRepository.findByPublicUuid(competencyPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Competency", competencyPublicUuid));
		if (!competency.getCourse().getId().equals(evaluation.getTeacherAssignment().getCourse().getId())) {
			throw new ResourceNotFoundException("Competency", competencyPublicUuid);
		}
		return resultRepository.findOne(evaluation, student, competency)
				.map(revisionRepository::findAllByResultOrderByRevisionDesc)
				.orElse(List.of()).stream()
				.map(revision -> new StudentCompetencyResultRevisionResponse(
						revision.getRevision(), revision.getSnapshot(),
						revision.getChangedByUserId(), revision.getChangedAt()))
				.toList();
	}

	@Override
	@Transactional
	public List<StudentCompetencyResultResponse> replace(UUID evaluationPublicUuid, UUID studentPublicUuid,
			UpsertStudentCompetencyResultsRequest request) {
		Evaluation evaluation = loadEvaluation(evaluationPublicUuid);
		ensureEvaluationAccess(evaluation);
		Student student = loadStudent(studentPublicUuid);
		ensureWritable(evaluation);
		ensureStudentInEvaluationSection(evaluation, student);

		UUID actor = currentUserProvider.currentUserId().orElseThrow(
				() -> new UnauthorizedException("Authenticated user is required to record competency results"));
		List<StudentCompetencyResult> existing = resultRepository.findActive(evaluation, student);
		Set<UUID> submittedCompetencies = new HashSet<>();
		List<StudentCompetencyResultResponse> saved = new ArrayList<>();

		for (StudentCompetencyResultInput input : request.results()) {
			if (!submittedCompetencies.add(input.competencyPublicUuid())) {
				throw new BadRequestException("DUPLICATE_COMPETENCY_RESULT",
						"La misma competencia no puede aparecer más de una vez en la evaluación.");
			}
			Competency competency = competencyRepository.findByPublicUuid(input.competencyPublicUuid())
					.orElseThrow(() -> new ResourceNotFoundException("Competency", input.competencyPublicUuid()));
			if (!competency.getCourse().getId().equals(evaluation.getTeacherAssignment().getCourse().getId())) {
				throw new ConflictException("COMPETENCY_OUT_OF_EVALUATION_COURSE",
						"La competencia debe pertenecer al curso de la evaluación.");
			}
		if (!Boolean.TRUE.equals(competency.getIsActive())) {
				throw new ConflictException("COMPETENCY_INACTIVE", "No se pueden registrar resultados para una competencia inactiva.");
		}

			StudentCompetencyResult result = resultRepository.findForUpdate(evaluation, student, competency)
					.orElseGet(() -> newResult(evaluation, student, competency));
			apply(result, input, actor);
			result.setIsActive(true);
			StudentCompetencyResult persisted = resultRepository.saveAndFlush(result);
			appendRevision(persisted, actor);
			saved.add(toResponse(persisted));
		}

		for (StudentCompetencyResult prior : existing) {
			if (!submittedCompetencies.contains(prior.getCompetency().getPublicUuid())) {
				prior.setIsActive(false);
				prior.setRevision(prior.getRevision() + 1);
				prior.setRecordedAt(Instant.now());
				prior.setRecordedByUserId(actor);
				StudentCompetencyResult persisted = resultRepository.saveAndFlush(prior);
				appendRevision(persisted, actor);
			}
		}

		auditLogger.log(AuditAction.CUSTOM, RESOURCE, saved.isEmpty() ? null : saved.get(0).publicUuid(),
				"Resultados por competencia actualizados",
				Map.of("evaluationPublicUuid", evaluationPublicUuid.toString(),
						"studentPublicUuid", studentPublicUuid.toString(),
						"activeResultCount", saved.size()));
		return List.copyOf(saved);
	}

	private StudentCompetencyResult newResult(Evaluation evaluation, Student student, Competency competency) {
		StudentCompetencyResult result = new StudentCompetencyResult();
		result.setEvaluation(evaluation);
		result.setStudent(student);
		result.setCompetency(competency);
		result.setRevision(0);
		return result;
	}

	private void apply(StudentCompetencyResult result, StudentCompetencyResultInput input, UUID actor) {
		result.setAchievementLevel(input.achievementLevel().trim());
		result.setEvidenceSummary(blankToNull(input.evidenceSummary()));
		result.setDescriptiveConclusion(blankToNull(input.descriptiveConclusion()));
		result.setCriteriaResults(input.criteriaResults() == null ? List.of()
				: input.criteriaResults().stream().map(this::toMap).toList());
		result.setRevision(result.getRevision() == null || result.getRevision() == 0
				? 1 : result.getRevision() + 1);
		result.setRecordedAt(Instant.now());
		result.setRecordedByUserId(actor);
	}

	private Map<String, String> toMap(CriterionResultInput input) {
		Map<String, String> value = new LinkedHashMap<>();
		value.put("criterionName", input.criterionName().trim());
		value.put("achievementLevel", input.achievementLevel().trim());
		if (input.evidence() != null) value.put("evidence", input.evidence().trim());
		if (input.feedback() != null) value.put("feedback", input.feedback().trim());
		return value;
	}

	private void appendRevision(StudentCompetencyResult result, UUID actor) {
		Map<String, Object> snapshot = new HashMap<>();
		snapshot.put("competencyPublicUuid", result.getCompetency().getPublicUuid().toString());
		snapshot.put("achievementLevel", result.getAchievementLevel());
		snapshot.put("evidenceSummary", result.getEvidenceSummary());
		snapshot.put("descriptiveConclusion", result.getDescriptiveConclusion());
		snapshot.put("criteriaResults", result.getCriteriaResults());
		snapshot.put("isActive", result.getIsActive());
		StudentCompetencyResultRevision revision = new StudentCompetencyResultRevision();
		revision.setResult(result);
		revision.setRevision(result.getRevision());
		revision.setSnapshot(snapshot);
		revision.setChangedByUserId(actor);
		revision.setChangedAt(Instant.now());
		revisionRepository.save(revision);
	}

	private StudentCompetencyResultResponse toResponse(StudentCompetencyResult result) {
		List<CriterionResultInput> criteria = result.getCriteriaResults() == null ? List.of()
				: result.getCriteriaResults().stream().map(value -> new CriterionResultInput(
						value.getOrDefault("criterionName", ""),
						value.getOrDefault("achievementLevel", ""),
						value.get("evidence"), value.get("feedback"))).toList();
		return new StudentCompetencyResultResponse(result.getPublicUuid(),
				result.getCompetency().getPublicUuid(), result.getCompetency().getCode(),
					result.getCompetency().getName(), result.getAchievementLevel(),
					result.getEvidenceSummary(), result.getDescriptiveConclusion(), criteria,
					result.getRevision(), result.getRecordedAt(), result.getRecordedByUserId());
	}

	private Evaluation loadEvaluation(UUID publicUuid) {
		return evaluationRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Evaluation", publicUuid));
	}

	private Student loadStudent(UUID publicUuid) {
		return studentRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Student", publicUuid));
	}

	private void ensureStudentInEvaluationSection(Evaluation evaluation, Student student) {
		boolean enrolled = enrollmentRepository.existsActiveAt(student,
				evaluation.getTeacherAssignment().getSection(), evaluation.getScheduledDate());
		if (!enrolled) {
			throw new ResourceNotFoundException("Student", student.getPublicUuid());
		}
	}

	private void ensureEvaluationAccess(Evaluation evaluation) {
		if (currentUserIsTenantAdmin()) return;
		UUID callerUserId = currentUserProvider.currentUserId().orElse(null);
		UUID assignedUserId = evaluation.getTeacherAssignment().getTeacher().getUserId();
		if (callerUserId == null || assignedUserId == null || !callerUserId.equals(assignedUserId)) {
			throw new ResourceNotFoundException("Evaluation", evaluation.getPublicUuid());
		}
	}

	private static boolean currentUserIsTenantAdmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) return false;
		for (GrantedAuthority authority : auth.getAuthorities()) {
			String value = authority.getAuthority();
			if ("TENANT_ADMIN".equals(value) || "ROLE_TENANT_ADMIN".equals(value)) return true;
		}
		return false;
	}

	private static void ensureWritable(Evaluation evaluation) {
		if (evaluation.getStatus() == EvaluationStatus.CLOSED) {
			throw new ConflictException("EVALUATION_CLOSED",
					"La evaluación está cerrada; los resultados por competencia son de solo lectura.");
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
