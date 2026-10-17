package com.rodrilang.librarymanager.store.payment.service;

import com.rodrilang.librarymanager.store.payment.repository.StoreMercadoPagoOAuthStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class StoreMercadoPagoOAuthStateCleanupJob {
    private final StoreMercadoPagoOAuthStateRepository repository;

    @Scheduled(cron = "${app.store.payments.mercado-pago.oauth-state-cleanup-cron:0 20 3 * * *}")
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        repository.deleteOldStates(now, now.minus(Duration.ofDays(1)));
    }
}
