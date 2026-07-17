package com.edushift.modules.help.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

/**
 * Wrapper for the manifest file at {@code docs/manuales/manifest.json}.
 *
 * <p>The file shape is:
 * <pre>{@code
 * {
 *   "version": "1",
 *   "updatedAt": "2026-07-11",
 *   "manuals": [ { "role": "...", "title": "...", ... }, ... ]
 * }
 * }</pre>
 * This wrapper exposes {@link #manuals()} as the list served by the
 * {@code GET /api/v1/help/manuals} endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ManualManifest(
        String version,
        LocalDate updatedAt,
        List<ManualIndexEntry> manuals
) {}