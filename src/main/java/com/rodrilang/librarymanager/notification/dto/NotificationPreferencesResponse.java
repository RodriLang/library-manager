package com.rodrilang.librarymanager.notification.dto;

import java.util.List;

public record NotificationPreferencesResponse(
        boolean emailEnabled,
        String emailAddress,
        List<NotificationPreferenceItemResponse> events
) {}
