package com.rodrilang.librarymanager.shipping.returns.dto;
import java.util.List;
public record CreateStoreReturnRequest(Long shipmentId, String reason, String notes, List<StoreReturnItemRequest> items) {}
