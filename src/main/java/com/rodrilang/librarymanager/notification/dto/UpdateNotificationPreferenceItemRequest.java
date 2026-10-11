package com.rodrilang.librarymanager.notification.dto;

import com.rodrilang.librarymanager.notification.model.NotificationType;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceItemRequest(
        @NotNull NotificationType eventType,
        boolean inAppEnabled,
        boolean emailEnabled
) {}
