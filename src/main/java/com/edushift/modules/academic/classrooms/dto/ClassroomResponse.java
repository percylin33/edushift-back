package com.edushift.modules.academic.classrooms.dto;

import com.edushift.modules.academic.classrooms.entity.Classroom;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body for a {@link Classroom} row.
 */
public record ClassroomResponse(
		UUID publicUuid,
		String code,
		String name,
		Classroom.Type type,
		Integer capacity,
		String location,
		String description,
		Instant createdAt,
		Instant updatedAt
) {
	public static ClassroomResponse from(Classroom c) {
		return new ClassroomResponse(
				c.getPublicUuid(),
				c.getCode(),
				c.getName(),
				c.getType(),
				c.getCapacity(),
				c.getLocation(),
				c.getDescription(),
				c.getCreatedAt(),
				c.getUpdatedAt());
	}
}