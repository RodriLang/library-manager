package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListBatchResult;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;

import java.util.List;

public interface PriceListImportBatchService {

    PriceListBatchResult processBatch(List<PriceListRow> rows, Long jobId);
}