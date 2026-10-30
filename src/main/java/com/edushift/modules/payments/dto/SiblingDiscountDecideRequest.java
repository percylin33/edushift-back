package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.Subscription;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record SiblingDiscountDecideRequest(
		@NotEmpty List<UUID> studentPublicUuids,
		@NotNull Subscription.SiblingDiscountStatus decision
) {
}
