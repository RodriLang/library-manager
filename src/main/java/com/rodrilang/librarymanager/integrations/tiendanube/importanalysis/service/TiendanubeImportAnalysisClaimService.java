package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeClaimedImportAnalysisRun;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository.TiendanubeImportAnalysisRunRepository;
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
public class TiendanubeImportAnalysisClaimService {

    private final TiendanubeImportAnalysisRunRepository runRepository;

    @Value("${tiendanube.import-analysis.lease-seconds:1800}")
    private long leaseSeconds;

    @Transactional
    public List<TiendanubeClaimedImportAnalysisRun> claim(int batchSize) {
        List<TiendanubeClaimedImportAnalysisRun> claimed = new ArrayList<>();
        Instant now = Instant.now();
        Instant leaseUntil = now.plus(Math.max(60, leaseSeconds), ChronoUnit.SECONDS);

        for (int index = 0; index < batchSize; index++) {
            Optional<TiendanubeClaimedImportAnalysisRun> run = runRepository.claimOne(
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

    @Transactional(readOnly = true)
    public Optional<Instant> findNextWakeAt() {
        return runRepository.findNextWakeAt(Instant.now());
    }
}
