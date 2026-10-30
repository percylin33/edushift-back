package com.edushift.modules.disclosures.dto;

/** Catalog entry displayed by the school editor and used to validate content. */
public record DisclosureFieldDefinition(String key, String section, String label, String guidance) { }
