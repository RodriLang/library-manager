package com.rodrilang.librarymanager.store.order.dto;

import com.rodrilang.librarymanager.payment.model.PaymentMethod;
import jakarta.validation.constraints.Size;

public record CompleteStoreOrderRequest(
        PaymentMethod paymentMethod,
        @Size(max = 100) String paymentReference
) {}
