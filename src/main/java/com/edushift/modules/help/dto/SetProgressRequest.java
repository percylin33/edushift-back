package com.edushift.modules.help.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for `PUT /api/v1/help/progress/{role}/{file}/{itemId}`.
 * Toggles (or sets) the checked state of one checklist item.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SetProgressRequest(
        @NotBlank @Size(max = 64) String itemId,
        boolean checked
) {}