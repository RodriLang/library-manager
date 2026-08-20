package com.rodrilang.librarymanager.service;

import com.rodrilang.librarymanager.importer.price.dto.internal.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListResolvedPrice;
import com.rodrilang.librarymanager.model.EditorialPrice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EditorialPriceService {

    PriceImportCounters registerResolvedPrices(
            Long jobId,
            List<PriceListResolvedPrice> prices
    );

    Optional<EditorialPrice> findCurrentByBookId(Long bookId);

    Map<Long, BigDecimal> findCurrentPricesByBookIds(List<Long> bookIds);
}