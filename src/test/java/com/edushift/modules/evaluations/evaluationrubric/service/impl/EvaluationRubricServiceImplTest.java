package com.edushift.modules.evaluations.evaluationrubric.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.error.EvaluationErrorCodes;
import com.edushift.modules.evaluations.evaluationrubric.dto.AttachRubricRequest;
import com.edushift.modules.evaluations.evaluationrubric.entity.EvaluationRubric;
import com.edushift.modules.evaluations.evaluationrubric.repository.EvaluationRubricRepository;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.evaluations.rubric.dto.RubricResponse;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import com.edushift.modules.evaluations.rubric.mapper.RubricMapper;
import com.edushift.modules.evaluations.rubric.repository.RubricRepository;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link EvaluationRubricServiceImpl} (Sprint 5B / BE-5B.4).
 *
 * <p>Covers attach (first-time, replace, no-op same rubric), get
 * (happy path, no rubric attached, unknown evaluation), detach
 * (happy path, no rubric attached) and the belt-and-braces tenant
 * cross-check (collapses to {@code RESOURCE_NOT_FOUND}).</p>
 *
 * <p>True multi-tenant isolation is covered by
 * {@code EvaluationRubricTenantIsolationIT} (Testcontainers).</p>
 */
@ExtendWith(MockitoExtension.class)
class EvaluationRubricServiceImplTest {

    @Mock private EvaluationRepository evaluationRepository;
    @Mock private RubricRepository rubricRepository;
    @Mock private EvaluationRubricRepository linkRepository;
    @Spy private RubricMapper rubricMapper = new RubricMapper();

    @InjectMocks private EvaluationRubricServiceImpl service;

    private UUID tenantId;
    private Evaluation evaluation;
    private Rubric rubric;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        evaluation = new Evaluation();
        setField(evaluation, "publicUuid", UUID.randomUUID());
        setField(evaluation, "id", UUID.randomUUID());
        setField(evaluation, "tenantId", tenantId);

        rubric = new Rubric();
        setField(rubric, "publicUuid", UUID.randomUUID());
        setField(rubric, "id", UUID.randomUUID());
        setField(rubric, "tenantId", tenantId);
        rubric.setName("Rúbrica X");
        rubric.setIsSystem(Boolean.FALSE);
        rubric.setIsActive(Boolean.TRUE);
        rubric.setCriteria(List.of());
        rubric.setLevels(List.of());
    }

    // =========================================================================
    // attachRubric
    // =========================================================================

    @Nested
    @DisplayName("attachRubric")
    class AttachRubric {

        @Test
        @DisplayName("first-time attach — inserts a new link and returns the rubric")
        void firstAttach() {
            given(evaluationRepository.findByPublicUuid(evaluation.getPublicUuid()))
                    .willReturn(Optional.of(evaluation));
            given(rubricRepository.findByPublicUuid(rubric.getPublicUuid()))
                    .willReturn(Optional.of(rubric));
            given(linkRepository.findActiveByEvaluation(evaluation))
                    .willReturn(Optional.empty());
            given(linkRepository.saveAndFlush(any(EvaluationRubric.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            RubricResponse response = service.attachRubric(
                    evaluation.getPublicUuid(),
                    new AttachRubricRequest(rubric.getPublicUuid().toString()));

            assertThat(response.publicUuid()).isEqualTo(rubric.getPublicUuid());
            verify(linkRepository).saveAndFlush(any(EvaluationRubric.class));
            verify(linkRepository, never()).delete(any(EvaluationRubric.class));
        }

        @Test
        @DisplayName("re-attach with a different rubric — soft-deletes old, "
                + "inserts new (replace)")
        void replace() {
            Rubric oldRubric = new Rubric();
            setField(oldRubric, "publicUuid", UUID.randomUUID());
            setField(oldRubric, "id", UUID.randomUUID());
            setField(oldRubric, "tenantId", tenantId);

            EvaluationRubric existing = new EvaluationRubric();
            existing.setEvaluation(evaluation);
            existing.setRubric(oldRubric);

            given(evaluationRepository.findByPublicUuid(evaluation.getPublicUuid()))
                    .willReturn(Optional.of(evaluation));
            given(rubricRepository.findByPublicUuid(rubric.getPublicUuid()))
                    .willReturn(Optional.of(rubric));
            given(linkRepository.findActiveByEvaluation(evaluation))
                    .willReturn(Optional.of(existing));
            given(linkRepository.saveAndFlush(any(EvaluationRubric.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            RubricResponse response = service.attachRubric(
                    evaluation.getPublicUuid(),
                    new AttachRubricRequest(rubric.getPublicUuid().toString()));

            assertThat(response.publicUuid()).isEqualTo(rubric.getPublicUuid());
            verify(linkRepository).delete(existing);
            verify(linkRepository).flush();
            verify(linkRepository).saveAndFlush(any(EvaluationRubric.class));
        }

        @Test
        @DisplayName("re-attach with same rubric — no-op (no delete, no insert)")
        void noOpSameRubric() {
            EvaluationRubric existing = new EvaluationRubric();
            existing.setEvaluation(evaluation);
            existing.setRubric(rubric);

            given(evaluationRepository.findByPublicUuid(evaluation.getPublicUuid()))
                    .willReturn(Optional.of(evaluation));
            given(rubricRepository.findByPublicUuid(rubric.getPublicUuid()))
                    .willReturn(Optional.of(rubric));
            given(linkRepository.findActiveByEvaluation(evaluation))
                    .willReturn(Optional.of(existing));

            RubricResponse response = service.attachRubric(
                    evaluation.getPublicUuid(),
                    new AttachRubricRequest(rubric.getPublicUuid().toString()));

            assertThat(response.publicUuid()).isEqualTo(rubric.getPublicUuid());
            verify(linkRepository, never()).delete(any(EvaluationRubric.class));
            verify(linkRepository, never()).saveAndFlush(any(EvaluationRubric.class));
        }

        @Test
        @DisplayName("unknown evaluation → 404 RESOURCE_NOT_FOUND")
        void unknownEvaluation() {
            UUID anyUuid = UUID.randomUUID();
            given(evaluationRepository.findByPublicUuid(anyUuid))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.attachRubric(anyUuid,
                    new AttachRubricRequest(rubric.getPublicUuid().toString())))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("unknown rubric → 404 RESOURCE_NOT_FOUND")
        void unknownRubric() {
            given(evaluationRepository.findByPublicUuid(evaluation.getPublicUuid()))
                    .willReturn(Optional.of(evaluation));
            given(rubricRepository.findByPublicUuid(any(UUID.class)))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.attachRubric(
                    evaluation.getPublicUuid(),
                    new AttachRubricRequest(UUID.randomUUID().toString())))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("rubric belongs to another tenant (tenant cross-check) "
                + "→ 404 RESOURCE_NOT_FOUND")
        void crossTenantRubric() {
            Rubric foreignRubric = new Rubric();
            setField(foreignRubric, "publicUuid", UUID.randomUUID());
            setField(foreignRubric, "id", UUID.randomUUID());
            // Different tenant id — would normally be filtered by Hibernate's
            // @TenantId, but if a mock leaks it through, the service still
            // collapses it to a 404 instead of accepting the link.
            setField(foreignRubric, "tenantId", UUID.randomUUID());

            given(evaluationRepository.findByPublicUuid(evaluation.getPublicUuid()))
                    .willReturn(Optional.of(evaluation));
            given(rubricRepository.findByPublicUuid(foreignRubric.getPublicUuid()))
                    .willReturn(Optional.of(foreignRubric));

            assertThatThrownBy(() -> service.attachRubric(
                    evaluation.getPublicUuid(),
                    new AttachRubricRequest(foreignRubric.getPublicUuid().toString())))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(linkRepository, never()).saveAndFlush(any(EvaluationRubric.class));
        }
    }

    // =========================================================================
    // getAttachedRubric
    // =========================================================================

    @Nested
    @DisplayName("getAttachedRubric")
    class GetAttachedRubric {

        @Test
        @DisplayName("happy path — returns the attached rubric")
        void happy() {
            EvaluationRubric link = new EvaluationRubric();
            link.setEvaluation(evaluation);
            link.setRubric(rubric);

            given(evaluationRepository.findByPublicUuid(evaluation.getPublicUuid()))
                    .willReturn(Optional.of(evaluation));
            given(linkRepository.findActiveByEvaluation(evaluation))
                    .willReturn(Optional.of(link));

            RubricResponse response = service.getAttachedRubric(
                    evaluation.getPublicUuid());

            assertThat(response.publicUuid()).isEqualTo(rubric.getPublicUuid());
        }

        @Test
        @DisplayName("no rubric attached → 404 EVAL_RUBRIC_NOT_SET")
        void notSet() {
            given(evaluationRepository.findByPublicUuid(evaluation.getPublicUuid()))
                    .willReturn(Optional.of(evaluation));
            given(linkRepository.findActiveByEvaluation(evaluation))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAttachedRubric(
                    evaluation.getPublicUuid()))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("code",
                            EvaluationErrorCodes.EVAL_RUBRIC_NOT_SET);
        }

        @Test
        @DisplayName("unknown evaluation → 404 RESOURCE_NOT_FOUND")
        void unknownEvaluation() {
            UUID anyUuid = UUID.randomUUID();
            given(evaluationRepository.findByPublicUuid(anyUuid))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAttachedRubric(anyUuid))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // detachRubric
    // =========================================================================

    @Nested
    @DisplayName("detachRubric")
    class DetachRubric {

        @Test
        @DisplayName("happy path — soft-deletes the active link")
        void happy() {
            EvaluationRubric link = new EvaluationRubric();
            link.setEvaluation(evaluation);
            link.setRubric(rubric);

            given(evaluationRepository.findByPublicUuid(evaluation.getPublicUuid()))
                    .willReturn(Optional.of(evaluation));
            given(linkRepository.findActiveByEvaluation(evaluation))
                    .willReturn(Optional.of(link));

            service.detachRubric(evaluation.getPublicUuid());

            verify(linkRepository, times(1)).delete(link);
        }

        @Test
        @DisplayName("no rubric attached → 404 EVAL_RUBRIC_NOT_SET (not idempotent)")
        void notSet() {
            given(evaluationRepository.findByPublicUuid(evaluation.getPublicUuid()))
                    .willReturn(Optional.of(evaluation));
            given(linkRepository.findActiveByEvaluation(evaluation))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.detachRubric(
                    evaluation.getPublicUuid()))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("code",
                            EvaluationErrorCodes.EVAL_RUBRIC_NOT_SET);
            verify(linkRepository, never()).delete(any(EvaluationRubric.class));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            f.set(target, value);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            }
            catch (NoSuchFieldException ignore) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
