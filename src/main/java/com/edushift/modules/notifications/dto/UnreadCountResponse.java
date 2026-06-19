package com.edushift.modules.notifications.dto;

/** Response for {@code GET /api/v1/notifications/unread-count}. */
public record UnreadCountResponse(long unreadCount) {}
