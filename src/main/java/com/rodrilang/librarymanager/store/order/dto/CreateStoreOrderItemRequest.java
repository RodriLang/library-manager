package com.rodrilang.librarymanager.store.order.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
public record CreateStoreOrderItemRequest(@NotNull Long inventoryId, @NotNull @Min(1) Integer quantity) {}
