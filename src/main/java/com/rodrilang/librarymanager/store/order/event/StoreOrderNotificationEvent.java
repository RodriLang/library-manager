package com.rodrilang.librarymanager.store.order.event;

import java.math.BigDecimal;
import java.util.List;

public record StoreOrderNotificationEvent(
        StoreOrderNotificationType type,
        Long orderId,
        String orderNumber,
        String storeName,
        String customerName,
        String customerEmail,
        BigDecimal total,
        String trackingUrl,
        String cancellationReason,
        List<Item> items
) {
    public record Item(
            String title,
            String isbn,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }
}
