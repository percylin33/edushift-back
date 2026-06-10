package com.edushift.modules.evaluations.evaluationrubric.service.impl;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.error.EvaluationErrorCodes;
import com.edushift.modules.evaluations.evaluationrubric.dto.AttachRubricRequest;
import com.edushift.modules.evaluations.evaluationrubric.entity.EvaluationRubric;
import com.edushift.modules.evaluations.evaluationrubric.repository.EvaluationRubricRepository;
import com.edushift.modules.evaluations.evaluationrubric.service.EvaluationRubricService;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.evaluations.rubric.dto.RubricResponse;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import com.edushift.modules.evaluations.rubric.mapper.RubricMapper;
import com.edushift.modules.evaluations.rubric.repository.RubricRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link EvaluationRubricService}
 * (Sprint 5B / BE-5B.4).
 *
 * <h3>Replace semantics</h3>
 * The MVP allows 0..1 rubrics per evaluation. The DB enforces it via
 * the partial unique index {@code uk_evaluation_rubric_evaluation
 * (evaluation_id) WHERE NOT deleted}. When a caller re-attaches with
 * a different rubric, the service explicitly soft-deletes the
 * previous link and {@code flush()}es it before the new INSERT, so
 * the partial unique index does not collide. Re-attaching the same
 * rubric is a no-op and returns the existing payload (200) — no
 * row is mutated.
 *
 * <h3>Multi-tenant defense in depth</h3>
 * The {@code @TenantId} discriminator filters both the evaluation and
 * rubric loaders, so cross-tenant UUIDs collapse to
 * {@code RESOURCE_NOT_FOUND} naturally. As a belt-and-braces guard we
 * additionally compare {@code evaluation.tenantId == rubric.tenantId}
 * before persisting; this would only fire in the (unreachable in
 * practice) scenario of a leaked cross-tenant entity instance, but it
 * is cheap and documents the invariant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationRubricServiceImpl implements EvaluationRubricService {

    private final EvaluationRepository evaluationRepository;
    private final RubricRepository rubricRepository;
    private final EvaluationRubricRepository linkRepository;
    private final RubricMapper rubricMapper;

    // =========================================================================
    // Reads
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public RubricResponse getAttachedRubric(UUID evaluationPublicUuid) {
        Evaluation evaluation = loadEvaluation(evaluationPublicUuid);
        EvaluationRubric link = linkRepository
                .findActiveByEvaluation(evaluation)
                .orElseThrow(() -> new NotFoundException(
                        EvaluationErrorCodes.EVAL_RUBRIC_NOT_SET,
                        "Evaluation " + evaluationPublicUuid
                                + " has no rubric attached"));
        return rubricMapper.toResponse(link.getRubric());
    }

    // =========================================================================
    // Writes
    // =========================================================================

    @Override
    @Transactional
    public RubricResponse attachRubric(UUID evaluationPublicUuid,
            AttachRubricRequest request) {
        Evaluation evaluation = loadEvaluation(evaluationPublicUuid);
        Rubric rubric = loadRubric(parseUuid(request.rubricPublicUuid()));

        // Belt-and-braces tenant cross-check. The @TenantId filter
        // already guarantees both rows live in the active tenant, so
        // their tenant_id values must match — this asserts the
        // invariant explicitly and would surface a 404 if some
        // future refactor weakens the filter.
        if (!evaluation.getTenantId().equals(rubric.getTenantId())) {
            throw new ResourceNotFoundException("Rubric",
                    request.rubricPublicUuid());
        }

        // Replace semantics: if the same rubric is already attached,
        // it's a no-op (return the existing payload). If a different
        // rubric is attached, soft-delete it and flush so the partial
        // unique index has no chance to race the INSERT below.
        Optional<EvaluationRubric> existing = linkRepository
                .findActiveByEvaluation(evaluation);
        if (existing.isPresent()) {
            EvaluationRubric current = existing.get();
            if (current.getRubric().getId().equals(rubric.getId())) {
                log.debug("[evaluation-rubric] no-op attach -- evaluation={} "
                                + "rubric={} (already attached)",
                        evaluation.getPublicUuid(), rubric.getPublicUuid());
                return rubricMapper.toResponse(rubric);
            }
            linkRepository.delete(current);
            linkRepository.flush();
            log.info("[evaluation-rubric] replaced -- evaluation={} "
                            + "rubric_old={} rubric_new={}",
                    evaluation.getPublicUuid(),
                    current.getRubric().getPublicUuid(),
                    rubric.getPublicUuid());
        }

        EvaluationRubric link = new EvaluationRubric();
        link.setEvaluation(evaluation);
        link.setRubric(rubric);
        linkRepository.saveAndFlush(link);

        log.info("[evaluation-rubric] attached -- evaluation={} rubric={}",
                evaluation.getPublicUuid(), rubric.getPublicUuid());

        return rubricMapper.toResponse(rubric);
    }

    @Override
    @Transactional
    public void detachRubric(UUID evaluationPublicUuid) {
        Evaluation evaluation = loadEvaluation(evaluationPublicUuid);
        EvaluationRubric link = linkRepository
                .findActiveByEvaluation(evaluation)
                .orElseThrow(() -> new NotFoundException(
                        EvaluationErrorCodes.EVAL_RUBRIC_NOT_SET,
                        "Evaluation " + evaluationPublicUuid
                                + " has no rubric attached"));
        linkRepository.delete(link);
        log.info("[evaluation-rubric] detached -- evaluation={} rubric={}",
                evaluation.getPublicUuid(), link.getRubric().getPublicUuid());
    }

    // =========================================================================
    // Loaders
    // =========================================================================

    private Evaluation loadEvaluation(UUID publicUuid) {
        return evaluationRepository.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Evaluation", publicUuid));
    }

    private Rubric loadRubric(UUID publicUuid) {
        return rubricRepository.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rubric", publicUuid));
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value.trim());
        }
        catch (IllegalArgumentException ex) {
            throw new BadRequestException("INVALID_UUID",
                    "Invalid UUID: " + value);
        }
    }
}
