package com.edushift.modules.sessions.learning.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.edushift.modules.ai.service.ApplicationSheetGeneratorService;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetKind;
import com.edushift.modules.sessions.learning.entity.LearningSession;
import com.edushift.modules.sessions.learning.repository.ApplicationSheetRepository;
import com.edushift.modules.sessions.learning.repository.LearningSessionRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.support.LmsAssignmentBinder;
import com.edushift.modules.tenants.service.TenantService;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Cross-tenant lookup looks like a missing session because Hibernate
 * filters by tenant. A colleague of another section is also 404.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationSheetServiceTest {

	@Mock private ApplicationSheetRepository sheetRepository;
	@Mock private LearningSessionRepository sessionRepository;
	@Mock private LmsAssignmentBinder lmsAssignmentBinder;
	@Mock private ApplicationSheetGeneratorService generatorService;
	@Mock private TenantService tenantService;

	private ApplicationSheetService service;
	private final ApplicationSheetPdfRenderer pdfRenderer = new ApplicationSheetPdfRenderer();

	@BeforeEach
	void setUp() {
		service = new ApplicationSheetService(
				sheetRepository,
				sessionRepository,
				lmsAssignmentBinder,
				generatorService,
				pdfRenderer,
				tenantService);
	}

	@Test
	void otherTenantCannotReadOrExport() {
		UUID sessionId = UUID.randomUUID();
		when(sessionRepository.findByPublicUuid(sessionId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.list(sessionId))
				.isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.exportPdf(sessionId, ApplicationSheetKind.LEARNING, "3.er Grado"))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void otherTeacherCannotReadOrExport() {
		UUID sessionId = UUID.randomUUID();
		LearningSession session = new LearningSession();
		session.setPublicUuid(sessionId);
		session.setTeacherAssignment(new TeacherAssignment());
		when(sessionRepository.findByPublicUuid(sessionId)).thenReturn(Optional.of(session));
		doThrow(new ForbiddenException("not your assignment"))
				.when(lmsAssignmentBinder).assertCallerMayUseAssignment(session.getTeacherAssignment());

		assertThatThrownBy(() -> service.list(sessionId))
				.isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.exportPdf(sessionId, ApplicationSheetKind.LEARNING, "3.er Grado"))
				.isInstanceOf(ResourceNotFoundException.class);
	}
}
