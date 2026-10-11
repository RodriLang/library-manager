package com.rodrilang.librarymanager.notification.event;

import com.rodrilang.librarymanager.notification.service.NotificationService;
import com.rodrilang.librarymanager.notification.service.NotificationSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationSseEventListener {

    private final NotificationService notificationService;
    private final NotificationSseService sseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        notificationService.findForBookstore(event.notificationId(), event.bookstoreId())
                .ifPresent(notification -> sseService.publish(event.bookstoreId(), notification));
    }
}
