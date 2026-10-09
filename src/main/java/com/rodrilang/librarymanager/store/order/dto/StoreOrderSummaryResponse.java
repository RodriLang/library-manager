package com.rodrilang.librarymanager.store.order.dto;

import com.rodrilang.librarymanager.store.order.model.*;
import java.math.BigDecimal;
import java.time.Instant;

public record StoreOrderSummaryResponse(
        Long id,
        String orderNumber,
        StoreOrderStatus status,
        StorePaymentStatus paymentStatus,
        StoreFulfillmentStatus fulfillmentStatus,
        String customerName,
        String customerEmail,
        BigDecimal total,
        Instant reservationExpiresAt,
        Instant createdAt
) {}
