package com.rodrilang.librarymanager.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateNotificationPreferencesRequest(
        boolean emailEnabled,
        @Email @Size(max = 320) String emailAddress,
        @Valid List<UpdateNotificationPreferenceItemRequest> events
) {}
