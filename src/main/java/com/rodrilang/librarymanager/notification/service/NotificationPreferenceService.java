package com.rodrilang.librarymanager.notification.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.notification.dto.*;
import com.rodrilang.librarymanager.notification.model.*;
import com.rodrilang.librarymanager.notification.repository.BookstoreNotificationPreferenceRepository;
import com.rodrilang.librarymanager.notification.repository.BookstoreNotificationSettingsRepository;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final BookstoreNotificationSettingsRepository settingsRepository;
    private final BookstoreNotificationPreferenceRepository preferenceRepository;
    private final BookstoreRepository bookstoreRepository;
    private final BookstoreContext bookstoreContext;

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getCurrent() {
        return response(bookstoreContext.getCurrentBookstoreId());
    }

    @Transactional
    public NotificationPreferencesResponse update(UpdateNotificationPreferencesRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la librería."));

        String email = normalize(request.emailAddress());
        if (request.emailEnabled() && email == null) {
            throw new BusinessException("Definí un email para activar las notificaciones por correo.");
        }

        BookstoreNotificationSettings settings = settingsRepository.findById(bookstoreId)
                .orElseGet(() -> BookstoreNotificationSettings.builder()
                        .bookstore(bookstore)
                        .bookstoreId(bookstoreId)
                        .build());
        settings.setEmailEnabled(request.emailEnabled());
        settings.setEmailAddress(email);
        settingsRepository.save(settings);

        Map<NotificationType, UpdateNotificationPreferenceItemRequest> requested = Optional.ofNullable(request.events())
                .orElseGet(List::of)
                .stream()
                .collect(Collectors.toMap(UpdateNotificationPreferenceItemRequest::eventType, Function.identity(), (a, b) -> b));

        for (NotificationType type : NotificationType.values()) {
            UpdateNotificationPreferenceItemRequest item = requested.get(type);
            if (item == null) continue;
            BookstoreNotificationPreference preference = preferenceRepository
                    .findByBookstoreIdAndEventType(bookstoreId, type)
                    .orElseGet(() -> BookstoreNotificationPreference.builder()
                            .bookstore(bookstore)
                            .eventType(type)
                            .build());
            preference.setInAppEnabled(item.inAppEnabled());
            preference.setEmailEnabled(item.emailEnabled());
            preferenceRepository.save(preference);
        }

        return response(bookstoreId);
    }

    @Transactional(readOnly = true)
    public ResolvedPreference resolve(Long bookstoreId, NotificationType type) {
        Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la librería."));
        BookstoreNotificationSettings settings = settingsRepository.findById(bookstoreId).orElse(null);
        BookstoreNotificationPreference preference = preferenceRepository
                .findByBookstoreIdAndEventType(bookstoreId, type)
                .orElse(null);

        boolean inApp = preference == null || preference.isInAppEnabled();
        boolean eventEmail = preference != null && preference.isEmailEnabled();
        boolean globalEmail = settings != null && settings.isEmailEnabled();
        String recipient = settings != null && normalize(settings.getEmailAddress()) != null
                ? normalize(settings.getEmailAddress())
                : normalize(bookstore.getEmail());

        return new ResolvedPreference(inApp, globalEmail && eventEmail && recipient != null, recipient);
    }

    private NotificationPreferencesResponse response(Long bookstoreId) {
        Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la librería."));
        BookstoreNotificationSettings settings = settingsRepository.findById(bookstoreId).orElse(null);
        Map<NotificationType, BookstoreNotificationPreference> preferences = preferenceRepository
                .findAllByBookstoreId(bookstoreId)
                .stream()
                .collect(Collectors.toMap(BookstoreNotificationPreference::getEventType, Function.identity()));

        List<NotificationPreferenceItemResponse> events = Arrays.stream(NotificationType.values())
                .map(type -> {
                    BookstoreNotificationPreference preference = preferences.get(type);
                    return new NotificationPreferenceItemResponse(
                            type,
                            preference == null || preference.isInAppEnabled(),
                            preference != null && preference.isEmailEnabled()
                    );
                })
                .toList();

        String email = settings != null && normalize(settings.getEmailAddress()) != null
                ? normalize(settings.getEmailAddress())
                : normalize(bookstore.getEmail());

        return new NotificationPreferencesResponse(
                settings != null && settings.isEmailEnabled(),
                email,
                events
        );
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public record ResolvedPreference(boolean inAppEnabled, boolean emailEnabled, String emailAddress) {}
}
