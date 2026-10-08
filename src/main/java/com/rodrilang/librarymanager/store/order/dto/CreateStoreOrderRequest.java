package com.rodrilang.librarymanager.store.order.dto;

import com.rodrilang.librarymanager.store.order.model.StoreDeliveryMethod;
import com.rodrilang.librarymanager.store.order.model.StorePaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record CreateStoreOrderRequest(
        @NotNull UUID clientRequestId,
        @NotBlank @Size(max = 200) String customerName,
        @Email @NotBlank @Size(max = 320) String customerEmail,
        @Size(max = 80) String customerPhone,
        @NotNull StoreDeliveryMethod deliveryMethod,
        @NotNull StorePaymentMethod paymentMethod,
        @Size(max = 2000) String notes,
        @NotEmpty @Size(max = 100) List<@Valid CreateStoreOrderItemRequest> items
) {}
