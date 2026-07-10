package com.rodrilang.librarymanager.metadata.cover.job;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CoverEnrichmentAsyncRunner {

    private final CoverEnrichmentJobProcessor processor;

    @Async
    public void run(Long jobId, int batchSize) {
        processor.process(jobId, batchSize);
    }
}