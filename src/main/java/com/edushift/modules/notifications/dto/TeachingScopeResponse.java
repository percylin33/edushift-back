package com.edushift.modules.notifications.dto;

import java.util.List;

/** Sections and courses a teacher may target with an announcement. */
public record TeachingScopeResponse(
        List<Option> sections,
        List<Option> courses
) {
    public record Option(String id, String label, String subtitle) {}
}
