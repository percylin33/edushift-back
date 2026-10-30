package com.edushift.modules.ai.rag;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AiPlatformSourceResponse(
        UUID publicUuid,
        String title,
        String status,
        List<String> audiences,
        String sourceUrl,
        String sourceVersion,
        LocalDate effectiveAt,
        LocalDate reviewAfter,
        String originalName,
        long sizeBytes,
        long textBytes,
        String indexErrorCode
) {}
