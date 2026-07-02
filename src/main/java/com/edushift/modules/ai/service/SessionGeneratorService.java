package com.edushift.modules.ai.service;

import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.competency.repository.CapacityRepository;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseLevelRepository;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.ai.dto.GenerateSessionRequest;
import com.edushift.modules.ai.dto.GenerateSessionResponse;
import com.edushift.modules.ai.entity.AiGeneration;
import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.exception.AiParseException;
import com.edushift.modules.ai.llm.LlmClient;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.ai.llm.LlmClient.LlmResponse;
import com.edushift.modules.ai.llm.LlmException;
import com.edushift.modules.ai.prompt.SessionGeneratorPromptBuilder;
import com.edushift.modules.ai.repository.AiGenerationRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the BE-8.1 "generate learning session outline" AI flow.
 *
 * <p>Mirrors the structure of {@link LmsAiService} (BE-7c.1) but
 * dedicated to the session-outline feature. Both services share the
 * same building blocks ({@link LlmClient}, {@link AiQuotaService},
 * {@link AiGenerationRepository}); the duplication of the
 * {@code PENDING → PROCESSING → LLM → parse → COMPLETED/FAILED} pipeline
 * is tracked as {@code DEBT-8-REFAC-1} (extract a
 * {@code BaseAiService} helper in Sprint 9).
 *
 * <h3>Input resolution</h3>
 * <ol>
 *   <li>{@link #verifyCourseInTenant(UUID)} — checks the courseId is
 *       in the caller's tenant (404 otherwise). This prevents
 *       cross-tenant injection of course names into the prompt.</li>
 *   <li>{@link #resolveCompetencyAndCapacityNames} — loads the
 *       teacher-picked competencies / capacities and feeds their NAMES
 *       (not UUIDs) into the prompt. Missing IDs are silently dropped
 *       (we do not want one bad ID to fail the whole generation).</li>
 * </ol>
 *
 * <h3>Output validation</h3>
 * After Jackson deserialises the LLM reply into
 * {@link GenerateSessionResponse}, the service calls
 * {@link GenerateSessionResponse#validate(String, int)} which enforces
 * the {@code session-generator/v1} contract (see ADR-8.2). Any
 * structural problem throws {@link AiParseException} (502) and a
 * {@code FAILED} audit row is persisted.
 */
@Service
public class SessionGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(SessionGeneratorService.class);

    private final LlmClient llmClient;
    private final SessionGeneratorPromptBuilder promptBuilder;
    private final AiQuotaService quotaService;
    private final AiGenerationRepository generationRepo;
    private final CurrentUserProvider currentUser;
    private final ObjectMapper objectMapper;

    private final CourseRepository courseRepository;
    private final CourseLevelRepository courseLevelRepository;
    private final CompetencyRepository competencyRepository;
    private final CapacityRepository capacityRepository;
    /**
     * Sprint 18 — when the LLM returns a usable outline we persist it
     * as a draft {@code LearningSession} so the teacher can review and
     * edit before starting. This is the "AI generation creates a real
     * session" gap from {@code 04-backlog.md} §2.
     */
    private final com.edushift.modules.sessions.learning.service.LearningSessionService learningSessionService;
    private final com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository teacherAssignmentRepository;

    public SessionGeneratorService(LlmClient llmClient,
                                   SessionGeneratorPromptBuilder promptBuilder,
                                   AiQuotaService quotaService,
                                   AiGenerationRepository generationRepo,
                                   CurrentUserProvider currentUser,
                                   ObjectMapper objectMapper,
                                   CourseRepository courseRepository,
                                   CourseLevelRepository courseLevelRepository,
                                   CompetencyRepository competencyRepository,
                                   CapacityRepository capacityRepository,
                                   com.edushift.modules.sessions.learning.service.LearningSessionService learningSessionService,
                                   com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository teacherAssignmentRepository) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.quotaService = quotaService;
        this.generationRepo = generationRepo;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
        this.courseRepository = courseRepository;
        this.courseLevelRepository = courseLevelRepository;
        this.competencyRepository = competencyRepository;
        this.capacityRepository = capacityRepository;
        this.learningSessionService = learningSessionService;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
    }

    /**
     * Generates a session outline synchronously. Throws
     * {@code AiDisabledException} / {@code AiQuotaExceededException} /
     * {@code CourseNotFoundException} (404) / {@code AiParseException}
     * / {@code LlmException} as documented on the class.
     */
    @Transactional
    public SessionGeneratorResult generateSession(GenerateSessionRequest request) {
        // 1. Quota gate. If this throws, no audit row is created.
        TenantAiSettings settings = quotaService.verifyCanCall();
        UUID tenantId = TenantContext.currentRequired();
        UUID userId = currentUser.currentUserId().orElse(null);

        // 2. Resolve course (multi-tenant safe — finds only in current tenant).
        Course course = courseRepository.findByPublicUuid(request.courseId())
                .orElseThrow(() -> new com.edushift.shared.exception.ResourceNotFoundException(
                        "Course", request.courseId().toString()));
        if (!course.getIsActive()) {
            // Inactive courses still resolve but the prompt will mention it.
            log.debug("Generating session for inactive course {} (id={})",
                    course.getCode(), course.getPublicUuid());
        }

        // 3. Resolve course level / grade name (for context). We pick the
        //    first level the course is offered at (the prompt just needs
        //    a hint; multiple-level detail would bloat the prompt).
        String gradeName = courseLevelRepository
                .findAllByCourse(course)
                .stream()
                .findFirst()
                .map(cl -> cl.getLevel().getName())
                .orElse(null);

        // 4. Resolve competency / capacity names (best-effort, never fail).
        List<String> competencyNames = resolveCompetencyNames(request.competencyIds());
        List<String> capacityNames = resolveCapacityNames(request.capacityIds());

        // 5. Build the prompt (this also normalises the inputs).
        LlmRequest llmRequest = promptBuilder.build(
                request, course.getName(), gradeName, competencyNames, capacityNames);

        // 6. Persist a PENDING audit row BEFORE the call. If the JVM
        //    dies mid-call, we still have a record of the attempt.
        AiGeneration gen = newGeneration(tenantId, userId, llmRequest, course.getPublicUuid());
        gen.setStatus(AiGeneration.Status.PENDING);
        generationRepo.saveAndFlush(gen);

        return runGeneration(gen, llmRequest, request, userId, course, tenantId);
    }

    @Transactional
    public SessionGeneratorResult runGeneration(AiGeneration gen,
                                                LlmRequest llmRequest,
                                                GenerateSessionRequest request,
                                                UUID userId,
                                                Course course,
                                                UUID tenantId) {
        gen.setStatus(AiGeneration.Status.PROCESSING);
        generationRepo.saveAndFlush(gen);

        long start = System.nanoTime();
        LlmResponse llmResponse;
        try {
            llmResponse = llmClient.complete(llmRequest);
        } catch (LlmException e) {
            long latency = (System.nanoTime() - start) / 1_000_000L;
            log.warn("Session-generation failed (code={}, latency={}ms): {}",
                    e.getCode(), latency, e.getMessage());
            markFailed(gen, e.getCode(), e.getMessage());
            quotaService.incrementCounters(false, 0L, 0L);
            throw e;
        } catch (RuntimeException e) {
            long latency = (System.nanoTime() - start) / 1_000_000L;
            log.error("Session-generation threw unexpected error (latency={}ms)", latency, e);
            markFailed(gen, "AI_UNEXPECTED", e.getMessage());
            quotaService.incrementCounters(false, 0L, 0L);
            throw e;
        }

        // Parse the LLM JSON + validate against the contract.
        GenerateSessionResponse parsed;
        try {
            parsed = parseAndValidate(llmResponse.text(), request.durationMinutes());
        } catch (AiParseException e) {
            long latency = (System.nanoTime() - start) / 1_000_000L;
            log.warn("Session-generation returned unparseable JSON (latency={}ms): {}",
                    latency, e.getMessage());
            markFailedWithResponse(gen, llmResponse, "AI_PARSE_ERROR", e.getMessage());
            quotaService.incrementCounters(false,
                    longOrZero(llmResponse.tokensIn()),
                    longOrZero(llmResponse.tokensOut()));
            throw e;
        }

        markCompleted(gen, llmResponse, parsed);
        quotaService.incrementCounters(true,
                longOrZero(llmResponse.tokensIn()),
                longOrZero(llmResponse.tokensOut()));

        // Sprint 18 — persist the LLM output as a draft `LearningSession`
        // so the teacher can review and edit before starting. We resolve
        // the teacher's primary assignment for (course, unit) and
        // delegate the actual entity creation to the existing
        // LearningSessionService so we don't duplicate the rich
        // validation (period window, unit-in-course, etc.).
        var persistedSession = persistAsLearningSession(
                request, userId, parsed, course, tenantId);

        return new SessionGeneratorResult(
                parsed,
                llmResponse.model(),
                llmClient.providerId(),
                SessionGeneratorPromptBuilder.PROMPT_VERSION,
                gen.getPublicUuid(),
                persistedSession);
    }

    /**
     * Persist the LLM-generated outline as a draft {@code LearningSession}.
     * Failure to persist (e.g. teacher has no assignment for the unit)
     * does NOT fail the generation — the teacher still gets the parsed
     * outline and can re-trigger persistence from the editor with the
     * correct unit. We log the failure for ops review.
     */
    private java.util.UUID persistAsLearningSession(
            GenerateSessionRequest request,
            java.util.UUID userId,
            com.edushift.modules.ai.dto.GenerateSessionResponse parsed,
            Course course,
            java.util.UUID tenantId) {
        if (userId == null) {
            // Anonymous / service token — no user to attach a session to.
            return null;
        }
        try {
            // Find an active assignment for the (teacher, course) pair.
            // The teacher is expected to have exactly one active assignment
            // per course; if multiple match we pick the most recent
            // (newest `assignedAt`).
            var assignments = teacherAssignmentRepository
                    .findActiveByCourseAndTeacherUuid(course, userId);
            if (assignments == null || assignments.isEmpty()) {
                log.warn("[ai/generate-session] no active assignment for teacher={} course={} — outline not persisted",
                        userId, course.getPublicUuid());
                return null;
            }
            var assignment = assignments.get(0);

            // The persisted SessionContentDto is intentionally minimal
            // (objective + activities[] + materials[] + observations);
            // we flatten the AI's rich structure into a single markdown-
            // style string per slot. The session editor lets the teacher
            // clean up before starting the session.
            var content = flattenToSessionContent(parsed);

            com.edushift.modules.sessions.learning.dto.CreateLearningSessionRequest createReq =
                    new com.edushift.modules.sessions.learning.dto.CreateLearningSessionRequest(
                            assignment.getPublicUuid(),
                            request.unitId(),
                            parsed.title() != null && !parsed.title().isBlank()
                                    ? parsed.title()
                                    : request.topic(),
                            parsed.summary(),
                            java.time.LocalDate.now(), // scheduledDate = today by default
                            request.durationMinutes(),
                            content,
                            request.competencyIds(),
                            request.capacityIds());
            var created = learningSessionService.create(createReq);
            log.info("[ai/generate-session] persisted draft session -- publicUuid={} title={}",
                    created.publicUuid(), createReq.title());
            return created.publicUuid();
        }
        catch (Exception e) {
            log.warn("[ai/generate-session] failed to persist outline (teacher can still see LLM output): {}",
                    e.getMessage());
            return null;
        }
    }

    /**
     * Flatten the AI's rich outline into the persisted session's
     * flat shape. Activities become "START (15 min) — Welcome";
     * resources become a "Name — url (kind) — notes" list; evaluation
     * criteria become a "Name (weight%): description" list. The teacher
     * can clean up in the editor.
     */
    private com.edushift.modules.sessions.learning.dto.SessionContentDto flattenToSessionContent(
            com.edushift.modules.ai.dto.GenerateSessionResponse parsed) {
        var activities = new java.util.ArrayList<String>();
        if (parsed.activities() != null) {
            for (var a : parsed.activities()) {
                String phase = a.phase() != null ? a.phase().name() : "-";
                String name = a.name() == null ? "" : a.name();
                String dur = a.durationMinutes() > 0 ? a.durationMinutes() + " min" : "";
                String desc = a.description() == null ? "" : a.description();
                activities.add(String.format("[%s] %s (%s) — %s", phase, name, dur, desc));
            }
        }
        var materials = new java.util.ArrayList<String>();
        if (parsed.resources() != null) {
            for (var r : parsed.resources()) {
                String name = r.title() == null ? "" : r.title();
                String url = r.url() == null ? "" : r.url();
                String kind = r.type() != null ? r.type().name() : "";
                String desc = r.description() == null ? "" : r.description();
                materials.add(String.format("%s — %s (%s) %s", name, url, kind, desc).trim());
            }
        }
        var observations = new StringBuilder();
        if (parsed.evaluationCriteria() != null) {
            observations.append("Criterios de evaluación:\n");
            for (var c : parsed.evaluationCriteria()) {
                String name = c.name() == null ? "" : c.name();
                String desc = c.description() == null ? "" : c.description();
                int weightPct = (int) Math.round(c.weight() * 100);
                observations.append(String.format("• %s (%d%%) — %s\n", name, weightPct, desc));
            }
        }
        return new com.edushift.modules.sessions.learning.dto.SessionContentDto(
                parsed.summary(),
                activities,
                materials,
                observations.length() == 0 ? null : observations.toString().trim());
    }

    // ---------------------------------------------------------------------
    // Input resolution.
    // ---------------------------------------------------------------------

    /**
     * Verifies the course is in the caller's tenant. The
     * {@code @TenantId} filter would mask cross-tenant lookups as
     * "not found" anyway, so we do not need a tenantId argument here.
     * Throws {@code ResourceNotFoundException} (404) if not found.
     */
    private void verifyCourseInTenant(UUID courseId) {
        courseRepository.findByPublicUuid(courseId)
                .orElseThrow(() -> new com.edushift.shared.exception.ResourceNotFoundException(
                        "Course", courseId.toString()));
    }

    private List<String> resolveCompetencyNames(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Competency> found = competencyRepository.findAllByPublicUuidIn(ids);
        List<String> names = new ArrayList<>(found.size());
        for (Competency c : found) {
            names.add(c.getCode() + " — " + c.getName());
        }
        if (found.size() != ids.size()) {
            log.debug("Some competencyIds were not found in the current tenant: requested={} found={}",
                    ids.size(), found.size());
        }
        return names;
    }

    private List<String> resolveCapacityNames(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Capacity> found = capacityRepository.findAllByPublicUuidIn(ids);
        List<String> names = new ArrayList<>(found.size());
        for (Capacity c : found) {
            names.add(c.getCode() + " — " + c.getName());
        }
        if (found.size() != ids.size()) {
            log.debug("Some capacityIds were not found in the current tenant: requested={} found={}",
                    ids.size(), found.size());
        }
        return names;
    }

    // ---------------------------------------------------------------------
    // Audit row helpers.
    // ---------------------------------------------------------------------

    private AiGeneration newGeneration(UUID tenantId, UUID userId, LlmRequest llmRequest, UUID courseId) {
        AiGeneration g = new AiGeneration();
        g.setPublicUuid(UUID.randomUUID());
        g.setTenantId(tenantId);
        g.setRequestUserId(userId);
        g.setFeature(AiGeneration.Feature.SESSION_OUTLINE_SUGGEST);
        g.setPromptText(llmRequest.userPrompt());
        // We do NOT persist the resolved courseId on the audit row (the
        // schema does not have such a column) — but we log it for debugging.
        log.debug("Session-generation start: tenant={} user={} course={} promptVersion={}",
                tenantId, userId, courseId, SessionGeneratorPromptBuilder.PROMPT_VERSION);
        return g;
    }

    private void markFailed(AiGeneration gen, String code, String message) {
        gen.setStatus(AiGeneration.Status.FAILED);
        gen.setErrorCode(code);
        gen.setErrorMessage(truncate(message, 4000));
        gen.setLatencyMs(0);
        generationRepo.save(gen);
    }

    private void markFailedWithResponse(AiGeneration gen, LlmResponse llmResponse,
                                        String code, String message) {
        gen.setStatus(AiGeneration.Status.FAILED);
        gen.setErrorCode(code);
        gen.setErrorMessage(truncate(message, 4000));
        gen.setResponseText(truncate(llmResponse.text(), 60_000));
        gen.setModelUsed(llmResponse.model());
        gen.setPromptTokens(llmResponse.tokensIn());
        gen.setResponseTokens(llmResponse.tokensOut());
        gen.setLatencyMs((int) Math.min(Integer.MAX_VALUE, llmResponse.latencyMs()));
        generationRepo.save(gen);
    }

    private void markCompleted(AiGeneration gen, LlmResponse llmResponse,
                               GenerateSessionResponse parsed) {
        gen.setStatus(AiGeneration.Status.COMPLETED);
        gen.setResponseText(truncate(llmResponse.text(), 60_000));
        // Persist the parsed JSON as a Map for the audit dashboard.
        Map<String, Object> parsedMap = objectMapper.convertValue(parsed,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        gen.setResponseParsed(parsedMap);
        gen.setModelUsed(llmResponse.model());
        gen.setPromptTokens(llmResponse.tokensIn());
        gen.setResponseTokens(llmResponse.tokensOut());
        gen.setLatencyMs((int) Math.min(Integer.MAX_VALUE, llmResponse.latencyMs()));
        generationRepo.save(gen);
    }

    // ---------------------------------------------------------------------
    // Output parsing.
    // ---------------------------------------------------------------------

    private GenerateSessionResponse parseAndValidate(String text, int expectedDuration) {
        GenerateSessionResponse parsed;
        try {
            parsed = objectMapper.readValue(text, GenerateSessionResponse.class);
        } catch (JsonProcessingException e) {
            throw new AiParseException(
                    "LLM response is not valid JSON: " + firstLine(e.getMessage()), e);
        }
        // validate() throws AiParseException on any structural problem.
        parsed.validate(text, expectedDuration);
        return parsed;
    }

    private static long longOrZero(Integer i) {
        return i == null ? 0L : i.longValue();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    /**
     * Result envelope. Includes the parsed response plus enough audit
     * metadata for the FE to show "Generated by {model} · UUID {generationUuid}".
     *
     * <p>Sprint 18: {@code persistedSessionUuid} carries the {@code publicUuid}
     * of the draft {@code LearningSession} created from the LLM output, so
     * the FE can navigate the user straight to the editor. {@code null} if
     * the persistence step was skipped (no active assignment, or any
     * non-fatal failure during create).
     */
    public record SessionGeneratorResult(
            GenerateSessionResponse session,
            String model,
            String provider,
            String promptVersion,
            UUID generationUuid,
            UUID persistedSessionUuid
    ) {
    }
}
