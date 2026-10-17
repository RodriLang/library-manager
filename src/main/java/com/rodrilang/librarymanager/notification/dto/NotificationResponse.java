package com.rodrilang.librarymanager.notification.dto;

import com.rodrilang.librarymanager.notification.model.NotificationEntityType;
import com.rodrilang.librarymanager.notification.model.NotificationSource;
import com.rodrilang.librarymanager.notification.model.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        NotificationSource source,
        String title,
        String message,
        NotificationEntityType entityType,
        Long entityId,
        boolean read,
        Instant readAt,
        Instant createdAt
) {
}
