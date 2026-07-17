package com.edushift.modules.sessions.template.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SessionTemplateResponse(
    UUID publicUuid,
    String templateKey,
    String name,
    String description,
    Map<String, Object> schema,
    boolean isSystem,
    Instant createdAt
) {}
