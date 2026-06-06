package com.edushift.modules.students.enrollments.dto;

import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Payload of {@code POST /v1/enrollments/{publicUuid}/withdraw}.
 *
 * <p>The {@code status} must be a terminal value
 * ({@code WITHDRAWN | TRANSFERRED | GRADUATED}); ACTIVE is rejected
 * with {@code 400 INVALID_WITHDRAW_STATUS}. {@code withdrawnAt} cannot
 * be earlier than the original {@code enrolledAt} (the database CHECK
 * also enforces this).</p>
 */
public record WithdrawEnrollmentRequest(

		@NotNull(message = "status is required")
		StudentEnrollmentStatus status,

		@NotNull(message = "withdrawnAt is required")
		LocalDate withdrawnAt
) {
}
