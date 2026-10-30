package com.edushift.modules.disclosures.dto;

import java.time.Instant;
import java.util.UUID;

public record DisclosureVersionSummary(UUID publicUuid, int versionNumber, String contentSha256, Instant publishedAt) { }
