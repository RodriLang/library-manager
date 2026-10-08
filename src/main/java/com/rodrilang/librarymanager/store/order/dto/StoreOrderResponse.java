package com.rodrilang.librarymanager.store.order.dto;

import com.rodrilang.librarymanager.store.order.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoreOrderResponse(
        Long id,
        UUID publicId,
        String orderNumber,
        UUID trackingToken,
        StoreOrderStatus status,
        StorePaymentStatus paymentStatus,
        StoreFulfillmentStatus fulfillmentStatus,
        StorePaymentMethod paymentMethod,
        StoreDeliveryMethod deliveryMethod,
        String customerName,
        String customerEmail,
        String customerPhone,
        String notes,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal shippingCost,
        BigDecimal total,
        Instant reservationExpiresAt,
        Instant createdAt,
        Instant confirmedAt,
        Instant cancelledAt,
        String cancellationReason,
        Long saleId,
        Instant completedAt,
        List<StoreOrderItemResponse> items
) {}
