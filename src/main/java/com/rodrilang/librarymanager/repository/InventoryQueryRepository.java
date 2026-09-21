package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.dto.internal.InventoryAdvancedFilters;
import com.rodrilang.librarymanager.dto.internal.InventoryFilterOption;
import com.rodrilang.librarymanager.dto.internal.InventoryStockSummaryCounts;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.criteria.InventorySearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

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

    Page<InventoryFilterOption> searchFilterAuthors(
            Long bookstoreId,
            String query,
            Pageable pageable
    );

    Page<InventoryFilterOption> searchFilterPublishers(
            Long bookstoreId,
            String query,
            Pageable pageable
    );

    List<InventoryFilterOption> findFilterAuthorsByIds(
            Long bookstoreId,
            Collection<Long> authorIds
    );

    List<InventoryFilterOption> findFilterPublishersByIds(
            Long bookstoreId,
            Collection<Long> publisherIds
    );

    List<Long> findIds(
            Long bookstoreId,
            InventorySearchCriteria criteria,
            Collection<Long> excludedInventoryIds
    );
}