package com.edushift.modules.ai.rag;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AiSourceResponse(
        UUID publicUuid,
        UUID filePublicUuid,
        String title,
        AiSourceDocument.Kind kind,
        AiSourceDocument.Status status,
        List<String> audiences,
        String sourceUrl,
        String sourceVersion,
        LocalDate effectiveAt,
        LocalDate reviewAfter,
        long textBytes,
        String indexErrorCode
) {
    public static AiSourceResponse from(AiSourceDocument d) {
        return new AiSourceResponse(d.getPublicUuid(), d.getFilePublicUuid(),
                d.getTitle(), d.getKind(), d.getStatus(), d.getAudiences(),
                d.getSourceUrl(), d.getSourceVersion(), d.getEffectiveAt(),
                d.getReviewAfter(), d.getTextBytes(), d.getIndexErrorCode());
    }
}
