package com.edushift.modules.help.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

/**
 * One entry in the manual index returned by {@code GET /api/v1/help/manuals}.
 *
 * <p>Mirrors the JSON shape written in
 * {@code docs/manuales/manifest.json}. The service deserialises that file
 * into a list of these records and returns it under the standard
 * {@code ApiResponse} envelope.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ManualIndexEntry(
        String role,
        String title,
        String summary,
        String url,
        LocalDate updatedAt,
        String status
) {}