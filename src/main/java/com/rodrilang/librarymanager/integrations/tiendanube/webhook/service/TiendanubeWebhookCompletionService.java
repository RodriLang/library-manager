package com.rodrilang.librarymanager.integrations.tiendanube.webhook.service;

import com.rodrilang.librarymanager.integrations.tiendanube.webhook.dto.TiendanubeClaimedWebhookEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.webhook.repository.TiendanubeWebhookEventRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.work.enums.TiendanubeWorkType;
import com.rodrilang.librarymanager.integrations.tiendanube.work.service.TiendanubeWorkNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeWebhookCompletionService {

    private final TiendanubeWebhookEventRepository repository;
    private final TiendanubeWebhookRetryPolicy retryPolicy;
    private final TiendanubeWorkNotifier workNotifier;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(TiendanubeClaimedWebhookEvent event) {
        int updated = repository.markCompleted(event.id(), event.processingToken(), Instant.now());
        if (updated != 1) {
            log.warn("Stale Tiendanube webhook completion ignored. eventId={}", event.id());
            return;
        }

        workNotifier.notifyWork(TiendanubeWorkType.WEBHOOK);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(TiendanubeClaimedWebhookEvent event, RuntimeException exception) {
        Instant now = Instant.now();
        String errorType = exception.getClass().getSimpleName();
        String errorMessage = resolveMessage(exception);

        int updated;
        if (retryPolicy.canRetry(event.attemptCount(), event.maxAttempts())) {
            Instant nextAttemptAt = retryPolicy.nextAttemptAt(event.attemptCount(), now);
            updated = repository.markRetry(
                    event.id(),
                    event.processingToken(),
                    nextAttemptAt,
                    now,
                    errorType,
                    errorMessage
            );

            if (updated == 1) {
                log.warn(
                        "Tiendanube webhook scheduled for retry. eventId={} event={} storeId={} resourceId={} attempt={}/{} nextAttemptAt={} errorType={}",
                        event.id(), event.event(), event.storeId(), event.resourceId(), event.attemptCount(),
                        event.maxAttempts(), nextAttemptAt, errorType
                );
            }
        } else {
            updated = repository.markFailed(
                    event.id(),
                    event.processingToken(),
                    now,
                    errorType,
                    errorMessage
            );

            if (updated == 1) {
                log.error(
                        "Tiendanube webhook permanently failed. eventId={} event={} storeId={} resourceId={} attempts={} errorType={} errorMessage={}",
                        event.id(), event.event(), event.storeId(), event.resourceId(), event.attemptCount(),
                        errorType, errorMessage
                );
            }
        }

        if (updated != 1) {
            log.warn("Stale Tiendanube webhook failure ignored. eventId={}", event.id());
            return;
        }

        workNotifier.notifyWork(TiendanubeWorkType.WEBHOOK);
    }

    private String resolveMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getName() : message;
    }
}
