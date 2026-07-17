package com.edushift.modules.admin.subscriptions;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record B2BSubscriptionRequest(

        @NotNull UUID planId,

        LocalDate trialEndsAt,

        String notes
) {}
