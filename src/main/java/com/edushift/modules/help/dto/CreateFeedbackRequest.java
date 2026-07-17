package com.edushift.modules.help.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body for `POST /api/v1/help/feedback`. {@code chapterFile} is
 * optional — null means the feedback targets the manual as a whole.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateFeedbackRequest(
        @NotBlank @Size(max = 32) String role,
        @Size(max = 64) String chapterFile,
        @NotBlank @Size(max = 4000) String body
) {}