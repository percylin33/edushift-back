package com.edushift.modules.evaluations.service;

import com.edushift.modules.evaluations.dto.CreateEvaluationRequest;
import com.edushift.modules.evaluations.dto.EvaluationFilters;
import com.edushift.modules.evaluations.dto.EvaluationListItem;
import com.edushift.modules.evaluations.dto.EvaluationResponse;
import com.edushift.modules.evaluations.dto.UpdateEvaluationRequest;
import java.util.List;
import java.util.UUID;

/**
 * Public surface of the {@code Evaluation} aggregate (Sprint 5B / BE-5B.1).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@link #listEvaluations} — assignment-scoped listing with filters.</li>
 *   <li>{@link #getEvaluation} — full detail, including anchors and
 *       {@code gradeCount}.</li>
 *   <li>{@link #createEvaluation} — always starts in {@code DRAFT}.</li>
 *   <li>{@link #updateEvaluation} — partial-merge, editability matrix
 *       enforced in the service (DRAFT/PUBLISHED/CLOSED).</li>
 *   <li>{@link #publishEvaluation} / {@link #closeEvaluation} — lifecycle
 *       transitions.</li>
 *   <li>{@link #deleteEvaluation} — soft-delete, blocked when
 *       {@code GradeRecord}s reference the row (BE-5B.3).</li>
 * </ul>
 *
 * <h3>Error contract</h3>
 * <table>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>assignment or evaluation publicUuid unknown for the tenant
 *           (incl. cross-tenant)</td></tr>
 *   <tr><td>{@code EVAL_NAME_EXISTS}</td><td>409</td>
 *       <td>another evaluation in the same assignment already uses
 *           {@code name} (case-insensitive)</td></tr>
 *   <tr><td>{@code EVAL_DATE_INVERTED}</td><td>400</td>
 *       <td>{@code dueDate < scheduledDate}</td></tr>
 *   <tr><td>{@code EVAL_KIND_SCALE_MISMATCH}</td><td>400</td>
 *       <td>kind and scale combination is not in the allowed matrix
 *           (e.g. {@code EXAM} with a literal scale)</td></tr>
 *   <tr><td>{@code EVAL_NOT_EDITABLE}</td><td>409</td>
 *       <td>try to patch a field frozen while in {@code PUBLISHED}</td></tr>
 *   <tr><td>{@code EVAL_CLOSED}</td><td>409</td>
 *       <td>try to write against a {@code CLOSED} evaluation</td></tr>
 *   <tr><td>{@code EVAL_NOT_IN_ASSIGNMENT}</td><td>400</td>
 *       <td>anchor (unit/session) does not belong to the assignment's
 *           scope</td></tr>
 *   <tr><td>{@code EVAL_UNIT_NOT_IN_COURSE}</td><td>400</td>
 *       <td>anchor unit belongs to a different course than the
 *           assignment</td></tr>
 *   <tr><td>{@code EVAL_SESSION_NOT_IN_ASSIGNMENT}</td><td>400</td>
 *       <td>anchor session belongs to a different assignment</td></tr>
 *   <tr><td>{@code EVAL_HAS_GRADES}</td><td>409</td>
 *       <td>delete attempted while {@code GradeRecord}s reference the
 *           evaluation (BE-5B.3)</td></tr>
 *   <tr><td>{@code EVAL_ILLEGAL_TRANSITION}</td><td>400</td>
 *       <td>lifecycle jump that the state machine forbids</td></tr>
 * </table>
 */
public interface EvaluationService {

	List<EvaluationListItem> listEvaluations(UUID assignmentPublicUuid, EvaluationFilters filters);

	EvaluationResponse getEvaluation(UUID publicUuid);

	EvaluationResponse createEvaluation(UUID assignmentPublicUuid, CreateEvaluationRequest request);

	EvaluationResponse updateEvaluation(UUID publicUuid, UpdateEvaluationRequest request);

	EvaluationResponse publishEvaluation(UUID publicUuid);

	EvaluationResponse closeEvaluation(UUID publicUuid);

	void deleteEvaluation(UUID publicUuid);
}
