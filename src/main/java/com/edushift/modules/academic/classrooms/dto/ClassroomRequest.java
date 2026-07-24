package com.edushift.modules.academic.classrooms.dto;

import com.edushift.modules.academic.classrooms.entity.Classroom;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload of {@code POST /v1/academic/classrooms} and
 * {@code PUT  /v1/academic/classrooms/{uuid}}.
 */
public record ClassroomRequest(
		@NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]*$") String code,
		@NotBlank @Size(max = 120) String name,
		@NotNull Classroom.Type type,
		@Min(0) Integer capacity,
		@Size(max = 160) String location,
		@Size(max = 500) String description
) {
}