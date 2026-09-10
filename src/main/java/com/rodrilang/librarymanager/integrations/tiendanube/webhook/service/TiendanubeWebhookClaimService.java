package com.rodrilang.librarymanager.integrations.tiendanube.webhook.service;

import com.rodrilang.librarymanager.integrations.tiendanube.webhook.dto.TiendanubeClaimedWebhookEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.webhook.dto.TiendanubeWebhookPendingEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.webhook.repository.TiendanubeWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeWebhookClaimService {

    private final TiendanubeWebhookEventRepository repository;

    @Value("${tiendanube.webhook-inbox.batch-size:20}")
    private int batchSize;

    @Value("${tiendanube.webhook-inbox.lease-seconds:60}")
    private long leaseSeconds;

    @Transactional
    public List<TiendanubeClaimedWebhookEvent> claimDueEvents() {
        Instant now = Instant.now();
        int recovered = repository.recoverExpiredLeases(now);

        if (recovered > 0) {
            log.warn("Tiendanube webhook leases recovered. count={}", recovered);
        }

        List<TiendanubeWebhookPendingEvent> pending = repository.lockDueEvents(batchSize, now);
        if (pending.isEmpty()) {
            return List.of();
        }

        Instant leaseExpiresAt = now.plusSeconds(leaseSeconds);
        List<TiendanubeClaimedWebhookEvent> claimed = new ArrayList<>(pending.size());

        for (TiendanubeWebhookPendingEvent event : pending) {
            UUID token = UUID.randomUUID();
            int updated = repository.markProcessing(event.id(), token, now, leaseExpiresAt);

            if (updated != 1) {
                continue;
            }

            claimed.add(new TiendanubeClaimedWebhookEvent(
                    event.id(),
                    event.tiendanubeStoreId(),
                    event.storeId(),
                    event.event(),
                    event.resourceId(),
                    event.payload(),
                    event.attemptCount() + 1,
                    event.maxAttempts(),
                    token
            ));
        }

        return claimed;
    }
    @Transactional(readOnly = true)
    public java.util.Optional<Instant> findNextWakeAt() {
        return repository.findNextWakeAt();
    }

}
