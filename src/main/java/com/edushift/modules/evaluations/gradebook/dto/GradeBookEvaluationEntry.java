package com.edushift.modules.evaluations.gradebook.dto;

import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GradeBookEvaluationEntry(
        UUID publicUuid,
        String name,
        EvaluationKind kind,
        EvaluationScale scale,
        EvaluationStatus status,
        BigDecimal weight,
        LocalDate scheduledDate,
        UUID categoryPublicUuid,
        String categoryName
) {
}
