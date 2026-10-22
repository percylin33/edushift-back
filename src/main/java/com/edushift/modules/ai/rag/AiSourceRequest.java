package com.edushift.modules.ai.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AiSourceRequest(
        @NotNull UUID filePublicUuid,
        @NotBlank @Size(max = 240) String title,
        @NotNull AiSourceDocument.Kind kind,
        @NotEmpty List<AiSourceDocument.Audience> audiences,
        @Size(max = 1000) String sourceUrl,
        @Size(max = 120) String sourceVersion,
        LocalDate effectiveAt,
        LocalDate reviewAfter
) {}
