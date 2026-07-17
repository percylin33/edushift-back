package com.edushift.modules.admin.plans;

import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String name,
        String code,
        String description,
        int pricePerStudentCents,
        Integer maxStudents,
        Integer maxTeachers,
        int maxStorageMb,
        List<String> features,
        int sortOrder,
        boolean isActive
) {

    static PlanResponse from(PlatformPlan plan) {
        return new PlanResponse(
                plan.getId(), plan.getName(), plan.getCode(), plan.getDescription(),
                plan.getPricePerStudentCents(), plan.getMaxStudents(), plan.getMaxTeachers(),
                plan.getMaxStorageMb(), plan.getFeatures(), plan.getSortOrder(), plan.isActive());
    }
}
