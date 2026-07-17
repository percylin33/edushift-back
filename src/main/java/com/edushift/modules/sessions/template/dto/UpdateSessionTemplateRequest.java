package com.edushift.modules.sessions.template.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpdateSessionTemplateRequest(
    @Size(max = 200) String name,
    @Size(max = 500) String description,
    Map<String, Object> schema
) {
    public boolean isEmpty() {
        return name == null && description == null && schema == null;
    }
}
