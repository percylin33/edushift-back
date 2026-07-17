package com.edushift.modules.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JustifyRequest(
        @NotBlank @Size(max = 2000) String justificationText
) {}
