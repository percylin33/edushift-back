package com.edushift.modules.evaluations.evaluationrubric.service;

import com.edushift.modules.evaluations.evaluationrubric.dto.AttachRubricRequest;
import com.edushift.modules.evaluations.rubric.dto.RubricResponse;
import java.util.UUID;

/**
 * Public surface of the {@code EvaluationRubric} association
 * (Sprint 5B / BE-5B.4).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@link #attachRubric} — replace-style upsert: if the
 *       evaluation already has a rubric attached, the previous link
 *       is soft-deleted and a new row is inserted in the same
 *       transaction.</li>
 *   <li>{@link #getAttachedRubric} — returns the full
 *       {@link RubricResponse} of the currently attached rubric, or
 *       throws 404 {@code EVAL_RUBRIC_NOT_SET} when none is attached.</li>
 *   <li>{@link #detachRubric} — soft-deletes the active link, or
 *       throws 404 {@code EVAL_RUBRIC_NOT_SET} when nothing is
 *       attached. Idempotent-on-second-call is a non-goal here:
 *       calling DELETE twice is a client bug.</li>
 * </ul>
 *
 * <h3>Error contract</h3>
 * <table>
 *   <caption>EvaluationRubric error codes</caption>
 *   <tr><th>Code</th><th>HTTP</th><th>Cause</th></tr>
 *   <tr><td>{@code RESOURCE_NOT_FOUND}</td><td>404</td>
 *       <td>{@code evaluationPublicUuid} or {@code rubricPublicUuid}
 *           is unknown for the active tenant. <strong>Cross-tenant
 *           rubric attachments collapse here on purpose</strong>: we
 *           never leak the existence of another tenant's rubric via
 *           a separate "tenant mismatch" code.</td></tr>
 *   <tr><td>{@code EVAL_RUBRIC_NOT_SET}</td><td>404</td>
 *       <td>GET / DELETE on an evaluation that has no rubric
 *           attached.</td></tr>
 * </table>
 *
 * <p>Note: the lifecycle of the parent evaluation does not gate this
 * association. A teacher may attach / detach a rubric on a CLOSED
 * evaluation — it changes the rubric reference for reporting but
 * does not produce or modify any {@code GradeRecord}.</p>
 */
public interface EvaluationRubricService {

    /**
     * Attaches a rubric to an evaluation, replacing any existing
     * attachment.
     *
     * @param evaluationPublicUuid public UUID of the evaluation
     * @param request              payload with the rubric to attach
     * @return the full {@link RubricResponse} of the now-attached
     *         rubric (so the FE can render the detail without a
     *         second round-trip)
     */
    RubricResponse attachRubric(UUID evaluationPublicUuid, AttachRubricRequest request);

    /**
     * Returns the rubric currently attached to the evaluation.
     *
     * @throws com.edushift.shared.exception.NotFoundException
     *         {@code EVAL_RUBRIC_NOT_SET} when no rubric is attached
     */
    RubricResponse getAttachedRubric(UUID evaluationPublicUuid);

    /**
     * Soft-deletes the active rubric attachment.
     *
     * @throws com.edushift.shared.exception.NotFoundException
     *         {@code EVAL_RUBRIC_NOT_SET} when no rubric is attached
     */
    void detachRubric(UUID evaluationPublicUuid);
}
