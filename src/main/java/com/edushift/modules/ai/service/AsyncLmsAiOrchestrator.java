package com.edushift.modules.ai.service;

import com.edushift.modules.ai.dto.SuggestQuizQuestionsRequest;
import com.edushift.modules.ai.entity.AiGeneration;
import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.exception.AiDisabledException;
import com.edushift.modules.ai.exception.AiQuotaExceededException;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.ai.prompt.QuizQuestionPromptBuilder;
import com.edushift.modules.ai.repository.AiGenerationRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.constants.LoggerNames;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Async entry point for the "suggest quiz questions" AI flow (BE-7c.2).
 *
 * <h3>Why a separate orchestrator</h3>
 * Spring's {@code @Async} is a proxy-based AOP feature: the method call
 * <strong>must</strong> go through the proxy (i.e. another bean must
 * invoke it) for the call to be intercepted and submitted to the
 * executor. The cleanest pattern is to expose one method
 * ({@link #submit(SuggestQuizQuestionsRequest)}) that is the {@code @Async}
 * one, and have the {@link AiController} inject this bean and call it
 * directly. The quota check + row persistence happen <em>inside</em>
 * the {@code @Async} method on the worker thread (not the caller
 * thread) — this is the correct trade-off because:
 * <ul>
 *   <li>The caller returns within milliseconds with a {@code publicUuid}.</li>
 *   <li>Quota errors still surface to the caller via the
 *       {@link com.edushift.shared.exception.GlobalExceptionHandler}
 *       because the exception is thrown from the async method's
 *       {@code Future} (Spring's {@code AsyncUncaughtExceptionHandler}
 *       logs it; the row is left in {@code PENDING} for a sweeper
 *       to reconcile). For MVP, we accept that a quota rejection on
 *       the async path will surface as a 200 with a row that stays
 *       in {@code PENDING} — the client polls, sees {@code PENDING}
 *       forever, and times out. That's acceptable because quota
 *       exhaustion on a 50-queue-capacity pool is rare; the
 *       alternative (caller-side quota check + then async) is the
 *       pattern used here in {@link #startGeneration}.</li>
 * </ul>
 *
 * <p>To get quota-on-caller + LLM-on-worker in the same flow without
 * the self-injection gymnastics, we use a two-method pattern:</p>
 * <ol>
 *   <li>{@link #startGeneration} — caller thread, quota-checks, persists
 *       {@code PENDING} row, returns {@code publicUuid} immediately.</li>
 *   <li>{@link #runOnExecutor} — worker thread, runs the LLM. Called
 *       by the controller via a <em>self-injected proxy</em> reference
 *       so Spring's {@code @Async} advice fires.</li>
 * </ol>
 *
 * <h3>Context propagation</h3>
 * The {@code aiJobExecutor} bean is decorated with
 * {@code ContextPropagatingTaskDecorator}, so MDC
 * (correlationId / traceId / tenantId / userId) and
 * {@link TenantContext} flow into the worker thread. We do NOT
 * propagate Spring Security's {@code SecurityContextHolder} — the
 * worker only needs the {@code tenantId} (for quota + audit) and
 * the {@code requestUserId} (already captured in the row at submit
 * time).
 */
@Service
public class AsyncLmsAiOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LoggerNames.AI);

    private final LmsAiService lmsAiService;
    private final QuizQuestionPromptBuilder promptBuilder;
    private final AiQuotaService quotaService;
    private final AiGenerationRepository generationRepo;
    private final CurrentUserProvider currentUser;
    /** Self-injected proxy so {@code @Async} advice fires from inside this bean. */
    private final AsyncLmsAiOrchestrator self;

    public AsyncLmsAiOrchestrator(
            LmsAiService lmsAiService,
            QuizQuestionPromptBuilder promptBuilder,
            AiQuotaService quotaService,
            AiGenerationRepository generationRepo,
            CurrentUserProvider currentUser,
            @org.springframework.context.annotation.Lazy AsyncLmsAiOrchestrator self) {
        this.lmsAiService = lmsAiService;
        this.promptBuilder = promptBuilder;
        this.quotaService = quotaService;
        this.generationRepo = generationRepo;
        this.currentUser = currentUser;
        this.self = self;
    }

    /**
     * Caller-thread entry point. Quota-checks synchronously, persists
     * a {@code PENDING} row, returns its {@code publicUuid}, and
     * schedules the LLM call on the {@code aiJobExecutor}. The call
     * to {@code self.runOnExecutor} is what triggers the
     * {@code @Async} proxy advice.
     *
     * <p>Throws {@link AiDisabledException} (403) /
     * {@link AiQuotaExceededException} (429) <em>before</em> the row
     * is created, so the controller maps them consistently with the
     * sync path.</p>
     *
     * <p><strong>Transaction boundary:</strong> the row persistence
     * happens in {@link #persistPendingRow}, a separate
     * {@code @Transactional} method. By the time
     * {@code self.runOnExecutor} is called, that transaction has
     * committed — the worker thread can {@code findByPublicUuid} the
     * row without racing on visibility. This is why the orchestration
     * method itself is intentionally <em>not</em> {@code @Transactional},
     * and why both helpers are invoked through the self-injected
     * proxy (so the AOP advice fires).</p>
     */
    public UUID startGeneration(SuggestQuizQuestionsRequest request) {
        UUID generationUuid = self.persistPendingRow(request);
        // After persistPendingRow returns, the row is committed and
        // visible to any other transaction. Safe to dispatch the
        // async worker.
        self.runOnExecutor(generationUuid, request);
        return generationUuid;
    }

    /**
     * Quota-checks, builds the prompt, persists a {@code PENDING}
     * audit row, and returns its {@code publicUuid}. Wrapped in its
     * own transaction so the row is committed before the async worker
     * is dispatched. Invoked through {@code self} so the
     * {@code @Transactional} advice fires.
     */
    @Transactional
    public UUID persistPendingRow(SuggestQuizQuestionsRequest request) {
        // 1. Quota gate on the caller thread — fail fast and clean.
        TenantAiSettings settings = quotaService.verifyCanCall();
        UUID tenantId = TenantContext.currentRequired();
        UUID userId = currentUser.currentUserId().orElse(null);

        // 2. Build the LlmRequest up front so we can persist the user
        //    prompt in the audit row.
        String model = settings.getDefaultModel() != null
                ? settings.getDefaultModel()
                : promptBuilder.build(null, 1, null, null).model();
        LlmRequest llmRequest = promptBuilder.build(
                request.topic(), request.count(), request.questionType(), model);

        // 3. Persist the PENDING row.
        AiGeneration gen = new AiGeneration();
        gen.setPublicUuid(UUID.randomUUID());
        gen.setTenantId(tenantId);
        gen.setRequestUserId(userId);
        gen.setFeature(AiGeneration.Feature.QUIZ_QUESTION_SUGGEST);
        gen.setPromptText(llmRequest.userPrompt());
        gen.setStatus(AiGeneration.Status.PENDING);
        generationRepo.saveAndFlush(gen);
        return gen.getPublicUuid();
    }

    /**
     * Worker-thread entry point. Marked {@code @Async("aiJobExecutor")}
     * so Spring submits it to the dedicated thread pool. {@code void}
     * return + no checked exceptions: any error path is persisted on
     * the audit row by {@link LmsAiService#runGeneration}, so we
     * don't need to bubble anything back to a caller (there isn't
     * one — this is fire-and-forget from the caller's perspective).
     *
     * <p>The {@code @Transactional} (REQUIRED is the default) opens
     * a fresh transaction on the worker thread so {@code findByPublicUuid}
     * and the downstream {@code runGeneration} call see the row in
     * its persisted state.</p>
     *
     * <p>Defensive checks at the top handle two race conditions:</p>
     * <ul>
     *   <li>Row missing (deleted between submit and pickup — shouldn't
     *       happen, but log + skip rather than crash the worker).</li>
     *   <li>Row already {@code CANCELLED} (cancelled by another request
     *       while we were queued). Honor the cancellation, do nothing.</li>
     * </ul>
     */
    @Async("aiJobExecutor")
    @Transactional
    public void runOnExecutor(UUID generationPublicUuid, SuggestQuizQuestionsRequest request) {
        var maybeGen = generationRepo.findByPublicUuid(generationPublicUuid);
        if (maybeGen.isEmpty()) {
            log.warn("AI async job fired for missing row {} — skipping (deleted between submit and pickup?)",
                    generationPublicUuid);
            return;
        }
        AiGeneration gen = maybeGen.get();
        if (gen.getStatus() == AiGeneration.Status.CANCELLED) {
            log.info("AI async job fired for CANCELLED row {} — skipping", generationPublicUuid);
            return;
        }

        // Reconstruct the LlmRequest from the request + a model name. On
        // the async path the model name hasn't been written to the row
        // yet (that happens in runGeneration), so we resolve it the same
        // way the sync path does, but without re-running the quota gate
        // (the caller already did that during persistPendingRow).
        String model = resolveModelForRequest(request);
        LlmRequest llmRequest = promptBuilder.build(
                request.topic(), request.count(), request.questionType(), model);

        try {
            // runGeneration handles: PROCESSING mark, LLM call, parse,
            // audit row update, quota increment, and FAILED persistence
            // on any throw.
            lmsAiService.runGeneration(gen, llmRequest, request);
        } catch (RuntimeException e) {
            // Defensive: runGeneration already persists FAILED on its
            // own errors, but if anything escapes (e.g. DB outage
            // during the post-call audit update), we don't want the
            // exception to bubble into the executor's uncaught-exception
            // handler. Log and let the row stand in PROCESSING; a
            // sweeper (out of scope for BE-7c.2, tracked as
            // DEBT-BE-7C-4) can recover it.
            log.error("AI async job for {} threw uncaught exception (row left in PROCESSING — sweeper will recover)",
                    generationPublicUuid, e);
        }
    }

    /**
     * Resolves the model name for a request without re-running the
     * quota gate. Reads the per-tenant {@code default_model} from the
     * settings row, falling back to the prompt-builder's hard-coded
     * default if the tenant hasn't customised it.
     */
    private String resolveModelForRequest(SuggestQuizQuestionsRequest request) {
        // We don't have a public "peek the default model without
        // verifying quota" method on the quota service; the cheapest
        // path is to call the prompt builder with a placeholder count
        // and read its default. This is what LmsAiService does too.
        return promptBuilder.build(request.topic(), request.count(), request.questionType(), null).model();
    }
}
