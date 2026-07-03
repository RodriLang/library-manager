package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;

public interface PriceListAsyncProcessor {

    void process(
            Long jobId,
            PriceListSource priceListSource,
            byte[] fileBytes
    );

}