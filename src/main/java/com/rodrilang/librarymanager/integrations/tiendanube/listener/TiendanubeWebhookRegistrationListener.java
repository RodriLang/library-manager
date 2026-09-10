package com.rodrilang.librarymanager.integrations.tiendanube.listener;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubeConnectedEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.webhook.service.TiendanubeWebhookRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "tiendanube.webhook-registration",
        name = "enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class TiendanubeWebhookRegistrationListener {

    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeWebhookRegistrationService registrationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleConnected(TiendanubeConnectedEvent event) {
        ensureRegistered(event.storeId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void handleApplicationReady() {
        ensureExistingConnections();
    }

    @Scheduled(
            cron = "${tiendanube.webhook-registration.cron:0 30 4 * * *}",
            zone = "${app.scheduling.zone:America/Argentina/Buenos_Aires}"
    )
    public void reconcileRegistrations() {
        ensureExistingConnections();
    }

    private void ensureExistingConnections() {
        for (TiendanubeStore store : storeRepository.findAllByActiveTrueAndTokenValidTrue()) {
            ensureRegistered(store.getStoreId());
        }
    }

    private void ensureRegistered(Long storeId) {
        try {
            registrationService.ensureRegistered(storeId);
        } catch (RuntimeException exception) {
            log.error("Could not ensure Tiendanube webhooks. storeId={}", storeId, exception);
        }
    }
}
