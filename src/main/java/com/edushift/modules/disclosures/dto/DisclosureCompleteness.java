package com.edushift.modules.disclosures.dto;

import java.util.List;

/** Review checklist; incomplete items warn but never prevent late publication. */
public record DisclosureCompleteness(int completed, int total, List<DisclosureFieldDefinition> pending) { }
