package com.edushift.modules.ai.dto;

import java.time.Instant;
import java.util.Map;

public record AiCapabilitiesResponse(
        String planCode,
        boolean aiEnabled,
        Map<String, Boolean> assistants,
        int requestsRemainingToday,
        long tokensRemainingThisMonth,
        Instant dailyResetAt,
        String blockedReason
) {}
