package com.rodrilang.librarymanager.service;

import com.rodrilang.librarymanager.dto.request.AddStockRequest;
import com.rodrilang.librarymanager.dto.request.RegisterManualBookPurchaseRequest;
import com.rodrilang.librarymanager.dto.request.RegisterPurchaseItemRequest;
import com.rodrilang.librarymanager.dto.request.UpdateInventoryStatusRequest;
import com.rodrilang.librarymanager.dto.request.UpdatePriceRequest;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface InventoryService {

    InventoryDetailResponse addStock(AddStockRequest request);

    InventoryDetailResponse updatePrice(Long inventoryId, UpdatePriceRequest request);

    InventoryDetailResponse updateActive(Long inventoryId, UpdateInventoryStatusRequest request);

    InventoryDetailResponse getByBookId(Long bookId);

    InventoryDetailResponse getByIsbn(String isbn);

    List<InventoryDetailResponse> registerPurchase(List<RegisterPurchaseItemRequest> request);

    InventoryDetailResponse registerPurchaseWithManualBook(RegisterManualBookPurchaseRequest request);

    Page<InventorySummaryResponse> getAll(Pageable pageable);

}