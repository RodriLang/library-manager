package com.rodrilang.librarymanager.store.order.dto;
import jakarta.validation.constraints.Size;
public record CancelStoreOrderRequest(@Size(max = 500) String reason) {}
