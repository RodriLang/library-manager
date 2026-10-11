package com.rodrilang.librarymanager.notification.service;

import com.rodrilang.librarymanager.email.service.EmailService;
import com.rodrilang.librarymanager.notification.service.NotificationDeliveryOutboxService.DeliveryPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEmailOutboxWorker {

    private static final int BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 5;

    private final NotificationDeliveryOutboxService outboxService;
    private final EmailService emailService;

    @Scheduled(fixedDelayString = "${app.notifications.email-outbox-delay-ms:10000}")
    public void deliver() {
        List<Long> ids = outboxService.claimDue(BATCH_SIZE, MAX_ATTEMPTS);
        for (Long id : ids) {
            DeliveryPayload payload = outboxService.loadPayload(id);
            if (payload == null) continue;
            try {
                emailService.sendBookstoreNotification(payload);
                outboxService.markSent(id);
            } catch (RuntimeException exception) {
                log.error("Notification email delivery failed deliveryId={} recipient={}", id, payload.recipient(), exception);
                outboxService.markFailed(id, exception.getMessage(), MAX_ATTEMPTS);
            }
        }
    }
}
