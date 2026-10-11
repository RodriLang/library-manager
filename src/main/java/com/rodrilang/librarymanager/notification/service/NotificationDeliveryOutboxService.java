package com.rodrilang.librarymanager.notification.service;

import com.rodrilang.librarymanager.notification.model.*;
import com.rodrilang.librarymanager.notification.repository.NotificationDeliveryOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryOutboxService {

    private final NotificationDeliveryOutboxRepository repository;

    @Transactional
    public void enqueueEmail(Notification notification, String recipient) {
        if (notification == null || notification.getId() == null || recipient == null || recipient.isBlank()) return;
        repository.save(NotificationDeliveryOutbox.builder()
                .notification(notification)
                .channel(NotificationDeliveryChannel.EMAIL)
                .recipient(recipient.trim().toLowerCase())
                .status(NotificationDeliveryStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(Instant.now())
                .build());
    }

    @Transactional
    public List<Long> claimDue(int batchSize, int maxAttempts) {
        List<NotificationDeliveryOutbox> rows = repository.lockDueBatch(batchSize, maxAttempts);
        Instant now = Instant.now();
        for (NotificationDeliveryOutbox row : rows) {
            row.setStatus(NotificationDeliveryStatus.PROCESSING);
            row.setAttempts(row.getAttempts() + 1);
            row.setLastError(null);
            row.setNextAttemptAt(now);
        }
        return rows.stream().map(NotificationDeliveryOutbox::getId).toList();
    }

    @Transactional(readOnly = true)
    public DeliveryPayload loadPayload(Long id) {
        NotificationDeliveryOutbox row = repository.findById(id).orElse(null);
        if (row == null || row.getStatus() != NotificationDeliveryStatus.PROCESSING) return null;
        Notification notification = row.getNotification();
        return new DeliveryPayload(
                row.getId(),
                row.getRecipient(),
                notification.getType(),
                notification.getSource(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getEntityType(),
                notification.getEntityId(),
                notification.getBookstore().getName()
        );
    }

    @Transactional
    public void markSent(Long id) {
        repository.findById(id).ifPresent(row -> {
            row.setStatus(NotificationDeliveryStatus.SENT);
            row.setSentAt(Instant.now());
            row.setLastError(null);
        });
    }

    @Transactional
    public void markFailed(Long id, String error, int maxAttempts) {
        repository.findById(id).ifPresent(row -> {
            row.setStatus(NotificationDeliveryStatus.FAILED);
            row.setLastError(limit(error, 1000));
            long delayMinutes = row.getAttempts() >= maxAttempts
                    ? 24 * 60L
                    : Math.min(60L, 1L << Math.min(6, Math.max(0, row.getAttempts() - 1)));
            row.setNextAttemptAt(Instant.now().plusSeconds(delayMinutes * 60));
        });
    }

    public record DeliveryPayload(
            Long deliveryId,
            String recipient,
            NotificationType type,
            NotificationSource source,
            String title,
            String message,
            NotificationEntityType entityType,
            Long entityId,
            String bookstoreName
    ) {}

    private String limit(String value, int max) {
        if (value == null || value.isBlank()) return "Error desconocido al enviar el email.";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
