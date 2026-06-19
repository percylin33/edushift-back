package com.edushift.modules.ai.dto;

import java.util.UUID;

/**
 * Response body for the async variant of
 * {@code POST /api/v1/lms/ai/quiz-questions} (BE-7c.2). Returned with
 * HTTP 202 Accepted.
 *
 * <p>The client polls {@code GET /v1/lms/ai/generations/{generationUuid}}
 * until the {@code status} becomes {@code COMPLETED}, {@code FAILED},
 * or {@code CANCELLED}.</p>
 *
 * @param generationUuid the {@code ai_generations.public_uuid} the
 *                       caller should poll on.
 * @param status         the row's status at submission time. Always
 *                       {@code PENDING} on a successful submit (the
 *                       controller only returns 202 after the row is
 *                       persisted; the worker thread promotes it to
 *                       {@code PROCESSING} / {@code COMPLETED} /
 *                       {@code FAILED}).
 * @param pollUrl        relative path the client should poll. We send
 *                       the path (not the absolute URL) so it works
 *                       across dev / staging / prod without rewriting.
 */
public record AsyncGenerationAcceptedResponse(
        UUID generationUuid,
        String status,
        String pollUrl
) {
    public static AsyncGenerationAcceptedResponse forUuid(UUID generationUuid) {
        return new AsyncGenerationAcceptedResponse(
                generationUuid,
                "PENDING",
                "/api/v1/lms/ai/generations/" + generationUuid
        );
    }
}
