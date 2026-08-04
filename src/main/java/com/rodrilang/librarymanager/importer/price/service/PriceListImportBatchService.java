package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.dto.PriceListBatchResult;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;

import java.util.List;

public interface PriceListImportBatchService {

    PriceListBatchResult processBatch(List<PriceListRow> rows, Long jobId);
}