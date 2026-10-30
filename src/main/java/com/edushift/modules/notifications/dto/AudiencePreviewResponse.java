package com.edushift.modules.notifications.dto;

/**
 * Distinct recipient counts. A person who is both a teacher and a
 * guardian is counted in each bucket and once in {@code total}.
 */
public record AudiencePreviewResponse(
        int students,
        int teachers,
        int guardians,
        int total
) {}
