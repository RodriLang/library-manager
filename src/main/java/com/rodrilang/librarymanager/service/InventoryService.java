package com.rodrilang.librarymanager.service;

import com.rodrilang.librarymanager.dto.request.AddBookToInventoryRequest;
import com.rodrilang.librarymanager.dto.request.InventoryQuantityRequest;
import com.rodrilang.librarymanager.dto.request.UpdateInventoryRequest;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryService {

    InventoryDetailResponse addToInventory(Long bookId, AddBookToInventoryRequest request);

    InventoryDetailResponse addStock(Long bookId, InventoryQuantityRequest request);

    InventoryDetailResponse recordSale(Long bookId, InventoryQuantityRequest request);

    InventoryDetailResponse update(Long bookId, UpdateInventoryRequest request);

    InventoryDetailResponse getByBookId(Long bookId);

    Page<InventorySummaryResponse> getAll(Pageable pageable);

    Page<InventorySummaryResponse> search(String query, Pageable pageable);

    void removeBook(Long bookId);

    void decreaseStockByBookId(Long bookId, Integer quantity);

    void increaseStockByBookId(Long bookId, Integer quantity);
}