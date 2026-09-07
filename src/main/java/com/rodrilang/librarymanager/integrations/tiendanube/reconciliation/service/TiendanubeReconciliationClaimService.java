package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeClaimedReconciliationRun;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.repository.TiendanubeReconciliationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TiendanubeReconciliationClaimService {

    private final TiendanubeReconciliationRepository reconciliationRepository;

    @Value("${tiendanube.reconciliation.lease-seconds:600}")
    private long leaseSeconds;

    @Transactional
    public List<TiendanubeClaimedReconciliationRun> claim(int batchSize) {
        List<TiendanubeClaimedReconciliationRun> claimed = new ArrayList<>();
        Instant now = Instant.now();
        Instant leaseUntil = now.plus(Math.max(30, leaseSeconds), ChronoUnit.SECONDS);

        for (int i = 0; i < batchSize; i++) {
            Optional<TiendanubeClaimedReconciliationRun> run = reconciliationRepository.claimOne(
                    now,
                    leaseUntil,
                    UUID.randomUUID()
            );

            if (run.isEmpty()) {
                break;
            }

            claimed.add(run.get());
        }

        return claimed;
    }
}
