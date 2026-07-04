package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import com.rodrilang.librarymanager.importer.price.service.PriceListAsyncProcessor;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class PriceListAsyncProcessorImpl implements PriceListAsyncProcessor {

    private final PriceListImportProcessor processor;

    @Async
    @Override
    public void process(
            Long jobId,
            PriceListSource priceListSource,
            LocalDate validFrom,
            byte[] fileBytes
    ) {
        processor.process(jobId, priceListSource, validFrom, fileBytes);
    }
}