package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeOAuthStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeOAuthStateCleanupService {

    private static final Duration USED_STATE_RETENTION = Duration.ofDays(1);

    private final TiendanubeOAuthStateRepository stateRepository;

    @Scheduled(
            cron = "${tiendanube.oauth-state-cleanup.cron:0 5 3 * * *}",
            zone = "${app.scheduling.zone:America/Argentina/Buenos_Aires}"
    )
    @Transactional
    public void deleteOldStates() {

        Instant now = Instant.now();

        int deleted = stateRepository.deleteOldStates(now, now.minus(USED_STATE_RETENTION));

        log.info("Estados OAuth de Tiendanube eliminados: {}", deleted);
    }
}