package com.rodrilang.librarymanager.notification.event;

public record NotificationCreatedEvent(Long notificationId, Long bookstoreId) {
}
