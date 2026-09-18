package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.dto.internal.InventoryAdvancedFilters;
import com.rodrilang.librarymanager.dto.internal.InventoryStockSummaryCounts;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.criteria.InventorySearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryQueryRepository {

    Page<Inventory> find(
            Long bookstoreId,
            InventorySearchCriteria criteria,
            Pageable pageable
    );

    InventoryStockSummaryCounts summarize(
            Long bookstoreId,
            InventoryAdvancedFilters filters
    );
}