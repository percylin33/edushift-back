package com.edushift.modules.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to update a notification template (Sprint 9 / BE-9.1).
 * Body is sanitized server-side; subject is plain text.
 */
public record UpdateTemplateRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank String bodyHtml
) {}
