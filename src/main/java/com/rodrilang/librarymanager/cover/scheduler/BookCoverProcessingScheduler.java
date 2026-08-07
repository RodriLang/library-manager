package com.rodrilang.librarymanager.cover.scheduler;

import com.rodrilang.librarymanager.cover.service.BookCoverProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "anaquel.cover-processing",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class BookCoverProcessingScheduler {

    private final BookCoverProcessingService processingService;

    @Scheduled(
            cron = "${anaquel.cover-processing.cron:0 0 3 * * MON}"
    )
    public void processPendingCandidates() {
        processingService.processNextBatch();
    }
}