package com.rodrilang.librarymanager.service;

import com.rodrilang.librarymanager.dto.internal.InventoryAdvancedFilters;
import com.rodrilang.librarymanager.dto.request.AddBookToInventoryRequest;
import com.rodrilang.librarymanager.dto.request.InventoryQuantityRequest;
import com.rodrilang.librarymanager.dto.request.InventorySaleRequest;
import com.rodrilang.librarymanager.dto.request.ReactivateInventoryRequest;
import com.rodrilang.librarymanager.dto.request.UpdateInventoryRequest;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventoryStockSummaryResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.repository.criteria.InventorySearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryService {

    InventoryDetailResponse addToInventory(Long bookId, AddBookToInventoryRequest request);

    InventoryDetailResponse addStock(Long bookId, InventoryQuantityRequest request);

    InventoryDetailResponse recordSale(Long bookId, InventorySaleRequest request);

    InventoryDetailResponse reactivate(Long bookId, ReactivateInventoryRequest request);

    InventoryDetailResponse update(Long bookId, UpdateInventoryRequest request);

    InventoryDetailResponse getById(Long bookId);

    InventoryDetailResponse getByBookId(Long bookId);

    Page<InventorySummaryResponse> find(InventorySearchCriteria criteria, Pageable pageable);

    InventoryStockSummaryResponse getStockSummary(InventoryAdvancedFilters filters);

    void deactivate(Long bookId);

    void recordTiendanubeSale(Long inventoryId, Integer quantity, String orderId);

    void restoreTiendanubeCancelledOrderStock(Long inventoryId, Integer quantity, String orderId);
}