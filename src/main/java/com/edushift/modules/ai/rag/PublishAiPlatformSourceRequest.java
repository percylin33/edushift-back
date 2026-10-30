package com.edushift.modules.ai.rag;

import jakarta.validation.constraints.AssertTrue;

/** Explicit human review acknowledgements required before global publication. */
public record PublishAiPlatformSourceRequest(
        @AssertTrue(message = "Confirma que el archivo coincide con la fuente oficial.")
        boolean officialUrlMatchesFile,
        @AssertTrue(message = "Confirma que la versión oficial está vigente.")
        boolean currentVersionConfirmed,
        @AssertTrue(message = "Confirma que revisaste el texto extraído.")
        boolean extractedTextReviewed,
        @AssertTrue(message = "Confirma las audiencias y fechas de publicación.")
        boolean audiencesAndDatesConfirmed
) {}
