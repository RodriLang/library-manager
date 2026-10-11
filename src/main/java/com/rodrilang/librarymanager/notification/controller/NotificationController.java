package com.rodrilang.librarymanager.notification.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.notification.dto.NotificationResponse;
import com.rodrilang.librarymanager.notification.dto.UnreadNotificationCountResponse;
import com.rodrilang.librarymanager.notification.dto.NotificationPreferencesResponse;
import com.rodrilang.librarymanager.notification.dto.UpdateNotificationPreferencesRequest;
import com.rodrilang.librarymanager.notification.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.notification.service.NotificationService;
import com.rodrilang.librarymanager.notification.service.NotificationSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;
    private final NotificationSseService sseService;
    private final BookstoreContext bookstoreContext;
    private final NotificationPreferenceService preferenceService;

    @GetMapping
    public PageResponse<NotificationResponse> list(Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return sseService.subscribe(bookstoreContext.getCurrentBookstoreId());
    }

    @GetMapping("/preferences")
    public NotificationPreferencesResponse preferences() {
        return preferenceService.getCurrent();
    }

    @PutMapping("/preferences")
    public NotificationPreferencesResponse updatePreferences(@Valid @RequestBody UpdateNotificationPreferencesRequest request) {
        return preferenceService.update(request);
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse unreadCount() {
        return service.unreadCount();
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable Long notificationId) {
        return service.markRead(notificationId);
    }

    @PostMapping("/read-all")
    public UnreadNotificationCountResponse markAllRead() {
        return service.markAllRead();
    }
}
