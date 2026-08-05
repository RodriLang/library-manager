package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.importer.price.service.PriceListAsyncProcessor;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class PriceListAsyncProcessorImpl implements PriceListAsyncProcessor {

    private final PriceListImportProcessor processor;

    @Async("priceListImportExecutor")
    @Override
    public void process(Long jobId, Path filePath) {
        processor.process(jobId, filePath);
    }
}