package com.rodrilang.librarymanager.notification.dto;

import com.rodrilang.librarymanager.notification.model.NotificationType;

public record NotificationPreferenceItemResponse(
        NotificationType eventType,
        boolean inAppEnabled,
        boolean emailEnabled
) {}
