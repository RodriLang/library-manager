package com.rodrilang.librarymanager.store.order.dto;
import com.rodrilang.librarymanager.store.order.model.StoreFulfillmentStatus;
import jakarta.validation.constraints.NotNull;
public record UpdateStoreFulfillmentRequest(@NotNull StoreFulfillmentStatus status) {}
