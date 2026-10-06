package com.edushift.modules.me.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Academic period option for the STUDENT "Mis cursos" period picker.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeSectionPeriodResponse(
        UUID publicUuid,
        String name,
        int ordinal,
        boolean current
) {
}
