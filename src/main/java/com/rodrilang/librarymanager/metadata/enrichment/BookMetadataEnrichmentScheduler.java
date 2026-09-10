package com.rodrilang.librarymanager.metadata.enrichment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookMetadataEnrichmentScheduler {

    private final BookMetadataEnrichmentService enrichmentService;

    @Scheduled(
            cron = "${anaquel.metadata-enrichment.cron:0 15 3 * * *}",
            zone = "${app.scheduling.zone:America/Argentina/Buenos_Aires}"
    )
    public void enrichPendingBooks() {
        int updated = enrichmentService.enrichPendingBooks(100);
        log.info("Enriquecimiento automático finalizado. Libros actualizados: {}", updated);
    }
}
