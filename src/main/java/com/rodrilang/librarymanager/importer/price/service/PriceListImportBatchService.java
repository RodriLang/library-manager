package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListBatchResult;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListStagingRow;

import java.util.List;

public interface PriceListImportBatchService {

    PriceListBatchResult processBatch(List<PriceListStagingRow> rows, Long jobId);
}