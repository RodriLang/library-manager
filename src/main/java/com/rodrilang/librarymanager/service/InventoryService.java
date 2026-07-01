package com.rodrilang.librarymanager.service;

import com.rodrilang.librarymanager.dto.request.AddStockRequest;
import com.rodrilang.librarymanager.dto.request.InventoryMovementRequest;
import com.rodrilang.librarymanager.dto.request.RegisterBookPurchaseRequest;
import com.rodrilang.librarymanager.dto.request.RegisterManualBookPurchaseRequest;
import com.rodrilang.librarymanager.dto.request.PurchaseItemRequest;
import com.rodrilang.librarymanager.dto.request.RegisterPurchaseRequest;
import com.rodrilang.librarymanager.dto.request.UpdateInventoryRequest;
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

    InventoryDetailResponse registerSale(Long bookId, InventoryMovementRequest request);

    InventoryDetailResponse registerReturn(Long bookId, InventoryMovementRequest request);

    InventoryDetailResponse getByIsbn(String isbn);

    InventoryDetailResponse updateByBookId(Long bookId, UpdateInventoryRequest request);

    List<InventoryDetailResponse> registerPurchase(RegisterPurchaseRequest request);

    InventoryDetailResponse registerPurchaseItem(Long bookId, RegisterBookPurchaseRequest item);

    InventoryDetailResponse registerPurchaseWithManualBook(RegisterManualBookPurchaseRequest request);

    Page<InventorySummaryResponse> getAll(Pageable pageable);

    Page<InventorySummaryResponse> search(String query, Pageable pageable);
}