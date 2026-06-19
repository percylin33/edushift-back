package com.edushift.modules.notifications.dto;

import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import jakarta.validation.constraints.NotNull;

/**
 * Request to update a single (channel × category) preference
 * (Sprint 9 / BE-9.1). The userId is taken from the JWT, not the
 * body (anti-enumeration).
 */
public record UpdatePreferenceRequest(
        @NotNull Channel channel,
        @NotNull Category category,
        @NotNull Boolean enabled
) {}
