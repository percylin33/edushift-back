package com.edushift.modules.evaluations.rubric.dto;

/**
 * Filter bag for
 * {@code GET /v1/academic/rubrics?systemOnly=...&active=...&q=...}.
 *
 * <p>All fields are optional. The service layer applies each present
 * filter as an AND conjunction; an empty {@code RubricFilters} maps to
 * "all non-deleted rubrics visible to the caller" (system + tenant-owned).</p>
 *
 * <h3>Filter semantics</h3>
 * <ul>
 *   <li>{@code systemOnly = true} → only {@code isSystem = true} rows
 *       (the seed MINEDU library).</li>
 *   <li>{@code systemOnly = false} → only tenant-owned rows (incl. forks).</li>
 *   <li>{@code systemOnly = null} → both, ordered system-first.</li>
 *   <li>{@code active = false} is allowed (deactivated rubrics remain
 *       visible to admins for audit, hidden from teachers' pickers).</li>
 *   <li>{@code q} is a case-insensitive {@code LIKE %q%} on {@code name}
 *       and {@code description}.</li>
 * </ul>
 */
public record RubricFilters(
		Boolean systemOnly,
		Boolean isActive,
		String q
) {
}
