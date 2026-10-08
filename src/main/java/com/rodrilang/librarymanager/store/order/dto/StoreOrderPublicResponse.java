package com.rodrilang.librarymanager.store.order.dto;

import com.rodrilang.librarymanager.store.order.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoreOrderPublicResponse(
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
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal total,
        Instant reservationExpiresAt,
        Instant createdAt,
        List<StoreOrderItemResponse> items
) {}
