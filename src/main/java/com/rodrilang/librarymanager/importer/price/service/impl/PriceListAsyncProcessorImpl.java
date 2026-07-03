package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import com.rodrilang.librarymanager.importer.price.service.PriceListAsyncProcessor;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PriceListAsyncProcessorImpl implements PriceListAsyncProcessor {

    private final PriceListImportProcessor processor;

    @Async
    @Override
    public void process(
            Long jobId,
            PriceListSource priceListSource,
            byte[] fileBytes
    ) {
        processor.process(jobId, priceListSource, fileBytes);
    }
}