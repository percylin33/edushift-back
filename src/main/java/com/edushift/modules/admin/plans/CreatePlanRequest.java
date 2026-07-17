package com.edushift.modules.admin.plans;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatePlanRequest(

        @NotBlank @Size(max = 50) String name,

        @NotBlank @Size(max = 30) String code,

        String description,

        @NotNull @Positive int pricePerStudentCents,

        Integer maxStudents,

        Integer maxTeachers,

        @NotNull int maxStorageMb,

        @NotNull List<String> features,

        int sortOrder
) {}
