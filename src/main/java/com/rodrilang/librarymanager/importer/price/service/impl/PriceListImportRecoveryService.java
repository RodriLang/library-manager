package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PriceListImportRecoveryService {

    private final PriceListImportJobRepository jobRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterruptedImports() {

        List<PriceListImportJob> jobs = jobRepository.findByStatus(PriceListImportJobStatus.PROCESSING);

        Instant now = Instant.now();

        for (PriceListImportJob job : jobs) {
            job.setStatus(PriceListImportJobStatus.FAILED);
            job.setFinishedAt(now);
            job.setErrorMessage("La importación fue interrumpida porque el servidor se reinició.");
        }
    }
}