package com.edushift.modules.sessions.learning.service;

import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.ai.dto.GenerateApplicationSheetRequest;
import com.edushift.modules.ai.dto.GenerateApplicationSheetRequest.OutlineActivity;
import com.edushift.modules.ai.dto.GenerateApplicationSheetRequest.OutlineCriterion;
import com.edushift.modules.ai.dto.GenerateApplicationSheetResponse;
import com.edushift.modules.ai.service.ApplicationSheetGeneratorService;
import com.edushift.modules.sessions.learning.dto.ApplicationSheetResponse;
import com.edushift.modules.sessions.learning.dto.ApplicationSheetWriteRequest;
import com.edushift.modules.sessions.learning.dto.GenerateStoredApplicationSheetRequest;
import com.edushift.modules.sessions.learning.entity.ApplicationSheet;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetKind;
import com.edushift.modules.sessions.learning.entity.LearningSession;
import com.edushift.modules.sessions.learning.entity.SessionContent;
import com.edushift.modules.sessions.learning.repository.ApplicationSheetRepository;
import com.edushift.modules.sessions.learning.repository.LearningSessionRepository;
import com.edushift.modules.sessions.learning.sheet.ApplicationSheetRules;
import com.edushift.modules.teachers.assignments.support.LmsAssignmentBinder;
import com.edushift.modules.tenants.service.TenantService;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationSheetService {

	private final ApplicationSheetRepository sheetRepository;
	private final LearningSessionRepository sessionRepository;
	private final LmsAssignmentBinder lmsAssignmentBinder;
	private final ApplicationSheetGeneratorService generatorService;
	private final ApplicationSheetPdfRenderer pdfRenderer;
	private final TenantService tenantService;

	@Transactional(readOnly = true)
	public List<ApplicationSheetResponse> list(UUID sessionUuid) {
		LearningSession session = loadSessionForCaller(sessionUuid);
		return sheetRepository.findBySessionPublicUuid(session.getPublicUuid()).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public List<ApplicationSheetResponse> replace(UUID sessionUuid, List<ApplicationSheetWriteRequest> sheets) {
		LearningSession session = loadSessionForCaller(sessionUuid);
		replace(session, sheets);
		return sheetRepository.findBySessionPublicUuid(session.getPublicUuid()).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public void replace(LearningSession session, List<ApplicationSheetWriteRequest> sheets) {
		if (sheets == null) {
			return;
		}
		Set<String> kept = new HashSet<>();
		for (ApplicationSheetWriteRequest sheet : sheets) {
			upsert(session, sheet);
			kept.add(key(sheet.kind(), sheet.grade()));
		}
		for (ApplicationSheet existing : sheetRepository.findBySessionPublicUuid(session.getPublicUuid())) {
			if (!kept.contains(key(existing.getKind(), existing.getGrade()))) {
				sheetRepository.delete(existing);
			}
		}
	}

	@Transactional
	public ApplicationSheetResponse generateForSession(UUID sessionUuid,
			GenerateStoredApplicationSheetRequest request) {
		LearningSession session = loadSessionForCaller(sessionUuid);
		GenerateApplicationSheetResponse generated = generatorService.generate(outlineFrom(session, request));
		ApplicationSheetWriteRequest write = new ApplicationSheetWriteRequest(
				generated.kind(),
				generated.grade(),
				generated.title(),
				generated.content());
		upsert(session, write);
		return sheetRepository
				.findBySessionKindAndGrade(session.getPublicUuid(), generated.kind(), normalize(generated.grade()))
				.map(this::toResponse)
				.orElseThrow(() -> new ResourceNotFoundException("ApplicationSheet", generated.grade()));
	}

	@Transactional(readOnly = true)
	public byte[] exportPdf(UUID sessionUuid, ApplicationSheetKind kind, String grade) {
		LearningSession session = loadSessionForCaller(sessionUuid);
		String normalized = normalize(grade);
		ApplicationSheet sheet = sheetRepository
				.findBySessionKindAndGrade(session.getPublicUuid(), kind, normalized)
				.orElseThrow(() -> new ResourceNotFoundException(
						"ApplicationSheet", kind + "/" + normalized));
		String courseName = session.getTeacherAssignment().getCourse() == null
				? ""
				: session.getTeacherAssignment().getCourse().getName();
		return pdfRenderer.render(sheet, courseName, tenantService.findCurrent().name());
	}

	private void upsert(LearningSession session, ApplicationSheetWriteRequest request) {
		String grade = normalize(request.grade());
		String problem = ApplicationSheetRules.validate(
				request.kind(), grade, request.title(), request.content()).orElse(null);
		if (problem != null) {
			throw new BadRequestException("SHEET_INVALID", problem);
		}
		ApplicationSheet sheet = sheetRepository
				.findBySessionKindAndGrade(session.getPublicUuid(), request.kind(), grade)
				.orElseGet(ApplicationSheet::new);
		boolean isNew = sheet.getId() == null;
		if (isNew) {
			sheet.setLearningSession(session);
			sheet.setKind(request.kind());
			sheet.setGrade(grade);
			sheet.setSheetNumber(nextNumber(session, request.kind(), grade));
		}
		sheet.setTitle(request.title().trim());
		sheet.setContent(request.content());
		sheetRepository.saveAndFlush(sheet);
	}

	private int nextNumber(LearningSession session, ApplicationSheetKind kind, String grade) {
		if (kind == ApplicationSheetKind.DIAGNOSTIC) {
			return sheetRepository
					.findBySessionKindAndGrade(session.getPublicUuid(), ApplicationSheetKind.LEARNING, grade)
					.map(ApplicationSheet::getSheetNumber)
					.orElse(1);
		}
		long existing = sheetRepository.countByAssignmentAndKind(
				session.getTeacherAssignment().getId(), ApplicationSheetKind.LEARNING);
		return (int) existing + 1;
	}

	private GenerateApplicationSheetRequest outlineFrom(LearningSession session,
			GenerateStoredApplicationSheetRequest request) {
		SessionContent content = session.getContent();
		List<OutlineActivity> activities = new ArrayList<>();
		if (content != null && content.getActivities() != null) {
			for (String activity : content.getActivities()) {
				if (activity == null || activity.isBlank()) {
					continue;
				}
				String name = activity.length() > 120 ? activity.substring(0, 120) : activity;
				activities.add(new OutlineActivity(null, name, activity));
			}
		}
		if (activities.isEmpty()) {
			String fallback = session.getObjective() == null || session.getObjective().isBlank()
					? session.getTitle()
					: session.getObjective();
			activities.add(new OutlineActivity(null, trim(session.getTitle(), 120), fallback));
		}
		List<String> competencies = new ArrayList<>();
		if (session.getCompetencies() != null) {
			for (Competency competency : session.getCompetencies()) {
				competencies.add(competency.getCode() + " — " + competency.getName());
			}
		}
		String summary = content != null && content.getObjective() != null && !content.getObjective().isBlank()
				? content.getObjective()
				: session.getObjective();
		String courseName = session.getTeacherAssignment().getCourse() == null
				? "Curso"
				: session.getTeacherAssignment().getCourse().getName();
		return new GenerateApplicationSheetRequest(
				request.kind(),
				normalize(request.grade()),
				courseName,
				session.getTitle(),
				session.getTitle(),
				summary,
				activities,
				competencies,
				List.<OutlineCriterion>of());
	}

	private LearningSession loadSessionForCaller(UUID publicUuid) {
		LearningSession session = sessionRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("LearningSession", publicUuid));
		try {
			lmsAssignmentBinder.assertCallerMayUseAssignment(session.getTeacherAssignment());
		}
		catch (ForbiddenException ex) {
			throw new ResourceNotFoundException("LearningSession", publicUuid);
		}
		return session;
	}

	private ApplicationSheetResponse toResponse(ApplicationSheet sheet) {
		return new ApplicationSheetResponse(
				sheet.getPublicUuid(),
				sheet.getKind(),
				sheet.getGrade(),
				sheet.getSheetNumber() == null ? 1 : sheet.getSheetNumber(),
				sheet.getTitle(),
				sheet.getContent());
	}

	private static String key(ApplicationSheetKind kind, String grade) {
		return kind.name() + "|" + normalize(grade);
	}

	static String normalize(String grade) {
		return grade == null ? "" : grade.trim();
	}

	private static String trim(String value, int max) {
		if (value == null) {
			return "";
		}
		String trimmed = value.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}
}
