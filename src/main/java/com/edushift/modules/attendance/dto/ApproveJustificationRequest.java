package com.edushift.modules.attendance.dto;

import jakarta.validation.constraints.NotNull;

public record ApproveJustificationRequest(
        @NotNull Boolean approved
) {}
