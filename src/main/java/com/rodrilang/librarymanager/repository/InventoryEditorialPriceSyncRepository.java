package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.dto.internal.InventoryEditorialPriceSyncResult;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Legacy adapter retained so old editorial-price administration code can keep calling the same contract.
 * Editorial prices no longer drive bookstore inventory sale prices.
 */
@Repository
public class InventoryEditorialPriceSyncRepository {

    public InventoryEditorialPriceSyncResult syncCurrentPrices(Collection<Long> bookIds, LocalDate currentDate) {
        return new InventoryEditorialPriceSyncResult(0, List.of());
    }
}
