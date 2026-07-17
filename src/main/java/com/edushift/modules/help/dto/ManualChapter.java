package com.edushift.modules.help.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

/**
 * One chapter (or README) of a role manual.
 *
 * <p>Returned by {@code GET /api/v1/help/manuals/{role}/{file}} where
 * {@code file} is one of {@code README.md}, {@code 01-onboarding-y-acceso.md},
 * etc. The path of the file inside the manual is preserved in {@link #path()}
 * so the FE can build breadcrumbs.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ManualChapter(
        String role,
        String path,
        String title,
        String content,
        LocalDate updatedAt
) {}