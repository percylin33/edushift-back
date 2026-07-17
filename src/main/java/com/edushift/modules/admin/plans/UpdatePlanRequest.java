package com.edushift.modules.admin.plans;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdatePlanRequest(

        @Size(max = 50) String name,

        @Size(max = 30) String code,

        String description,

        @Positive Integer pricePerStudentCents,

        Integer maxStudents,

        Integer maxTeachers,

        Integer maxStorageMb,

        List<String> features,

        Integer sortOrder,

        Boolean isActive
) {}
