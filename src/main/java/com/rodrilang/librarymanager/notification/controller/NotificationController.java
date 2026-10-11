package com.rodrilang.librarymanager.notification.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.notification.dto.NotificationResponse;
import com.rodrilang.librarymanager.notification.dto.UnreadNotificationCountResponse;
import com.rodrilang.librarymanager.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public PageResponse<NotificationResponse> list(Pageable pageable) {
        return service.list(pageable);
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
