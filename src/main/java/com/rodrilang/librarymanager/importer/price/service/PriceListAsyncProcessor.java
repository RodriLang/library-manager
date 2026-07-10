package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;

import java.time.LocalDate;

public interface PriceListAsyncProcessor {

    void process(
            Long jobId,
            PriceListSource priceListSource,
            LocalDate validFrom,
            byte[] fileBytes
    );

}