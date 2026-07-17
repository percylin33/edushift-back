package com.edushift.modules.sessions.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateSessionTemplateRequest(
    @NotBlank @Size(max = 100) String templateKey,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 500) String description,
    Map<String, Object> schema
) {}
