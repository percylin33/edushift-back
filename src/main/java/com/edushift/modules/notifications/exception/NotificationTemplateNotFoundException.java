package com.edushift.modules.notifications.exception;

import com.edushift.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Notification template not found (Sprint 9 / BE-9.1).
 *
 * <p>Thrown when TENANT_ADMIN tries to PATCH a template that doesn't
 * exist in their tenant. Maps to HTTP 404 with code
 * {@code NOTIFICATION_TEMPLATE_NOT_FOUND}.</p>
 */
public class NotificationTemplateNotFoundException extends ApiException {
    public NotificationTemplateNotFoundException() {
        super(HttpStatus.NOT_FOUND, "NOTIFICATION_TEMPLATE_NOT_FOUND",
                "Notification template not found in the current tenant");
    }
}
