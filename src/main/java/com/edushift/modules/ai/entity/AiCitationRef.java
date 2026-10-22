package com.edushift.modules.ai.entity;

import com.edushift.modules.ai.rag.AiSourceSearchService.Citation;
import java.time.LocalDate;
import java.util.UUID;

/** Historical source metadata. Passage text is not copied into chat history. */
public record AiCitationRef(UUID sourcePublicUuid, UUID filePublicUuid,
                            String fileName, String sourceScope, String title, String kind,
                            String sourceUrl, String sourceVersion,
                            LocalDate effectiveAt, Integer pageNumber, int ordinal) {
    public static AiCitationRef from(Citation citation) {
        return new AiCitationRef(citation.sourcePublicUuid(), citation.filePublicUuid(),
                citation.fileName(), citation.sourceScope(), citation.title(), citation.kind(),
                citation.sourceUrl(), citation.sourceVersion(),
                citation.effectiveAt(), citation.pageNumber(), citation.ordinal());
    }
}
