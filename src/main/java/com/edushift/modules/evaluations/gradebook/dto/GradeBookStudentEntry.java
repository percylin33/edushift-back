package com.edushift.modules.evaluations.gradebook.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record GradeBookStudentEntry(
        UUID publicUuid,
        String fullName,
        BigDecimal weightedAverage,
        String enrollmentStatus,
        Map<UUID, BigDecimal> categoryAverages,
        String literalAverage,
        Map<UUID, String> categoryLiteralAverages
) {
}
