package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.dto.internal.PriceImportCounters;

public interface PriceListResolvedPriceProcessor {

    PriceImportCounters process(Long jobId);
}