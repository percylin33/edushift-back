package com.edushift.modules.sessions.learning.service;

import com.edushift.modules.sessions.learning.dto.CreateLearningSessionRequest;
import com.edushift.modules.sessions.learning.dto.LearningSessionFilters;
import com.edushift.modules.sessions.learning.dto.LearningSessionListItem;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse;
import com.edushift.modules.sessions.learning.dto.LifecycleRequest;
import com.edushift.modules.sessions.learning.dto.UpdateLearningSessionRequest;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the {@code LearningSession} aggregate
 * (Sprint 5A / BE-5A.4).
 *
 * <p>Responsibilities split across this surface:</p>
 * <ul>
 *   <li><strong>CRUD</strong>: create / get / list-filtered / update /
 *       delete.</li>
 *   <li><strong>Reverse views</strong>: {@code listByAssignment},
 *       {@code listByUnit}.</li>
 *   <li><strong>Lifecycle</strong>: {@code start} / {@code complete} /
 *       {@code cancel}, all version-aware to be safe under concurrent
 *       writes.</li>
 * </ul>
 */
public interface LearningSessionService {

	// ---- CRUD ---------------------------------------------------------------

	LearningSessionResponse create(CreateLearningSessionRequest request);

	LearningSessionResponse get(UUID publicUuid);

	List<LearningSessionListItem> list(LearningSessionFilters filters);

	LearningSessionResponse update(UUID publicUuid, UpdateLearningSessionRequest request);

	void delete(UUID publicUuid);

	// ---- Reverse views ------------------------------------------------------

	List<LearningSessionListItem> listByAssignment(UUID assignmentUuid);

	List<LearningSessionListItem> listByUnit(UUID unitUuid);

	// ---- Lifecycle ----------------------------------------------------------

	LearningSessionResponse start(UUID publicUuid, LifecycleRequest request);

	LearningSessionResponse complete(UUID publicUuid, LifecycleRequest request);

	LearningSessionResponse cancel(UUID publicUuid, LifecycleRequest request);
}
