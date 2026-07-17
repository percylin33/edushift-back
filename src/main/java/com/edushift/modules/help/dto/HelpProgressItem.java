package com.edushift.modules.help.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * One progress row for a checklist item. Returned in
 * `GET /api/v1/help/progress/{role}/{file}`.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HelpProgressItem(
        String itemId,
        boolean checked,
        Instant updatedAt
) {}