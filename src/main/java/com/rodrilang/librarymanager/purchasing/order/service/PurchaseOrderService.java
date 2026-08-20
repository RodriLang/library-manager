package com.rodrilang.librarymanager.purchasing.order.service;

import com.rodrilang.librarymanager.purchasing.order.dto.PurchaseOrderFilter;
import com.rodrilang.librarymanager.purchasing.order.dto.request.AddPurchaseOrderItemRequest;
import com.rodrilang.librarymanager.purchasing.order.dto.request.CreatePurchaseOrderRequest;
import com.rodrilang.librarymanager.purchasing.order.dto.request.CreatePurchaseOrdersFromRequirementsRequest;
import com.rodrilang.librarymanager.purchasing.order.dto.request.UpdatePurchaseOrderItemRequest;
import com.rodrilang.librarymanager.purchasing.order.dto.response.CreatePurchaseOrdersFromRequirementsResponse;
import com.rodrilang.librarymanager.purchasing.order.dto.response.PurchaseOrderDetailResponse;
import com.rodrilang.librarymanager.purchasing.order.dto.response.PurchaseOrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PurchaseOrderService {

    PurchaseOrderDetailResponse create(CreatePurchaseOrderRequest request);

    CreatePurchaseOrdersFromRequirementsResponse createFromRequirements(
            CreatePurchaseOrdersFromRequirementsRequest request
    );

    Page<PurchaseOrderResponse> findAll(PurchaseOrderFilter filter, Pageable pageable);

    PurchaseOrderDetailResponse findById(Long orderId);

    PurchaseOrderDetailResponse addItem(Long orderId, AddPurchaseOrderItemRequest request);

    PurchaseOrderDetailResponse updateItem(Long orderId, Long itemId, UpdatePurchaseOrderItemRequest request);

    PurchaseOrderDetailResponse removeItem(Long orderId, Long itemId);

    PurchaseOrderDetailResponse send(Long orderId);

    void cancel(Long orderId);
}