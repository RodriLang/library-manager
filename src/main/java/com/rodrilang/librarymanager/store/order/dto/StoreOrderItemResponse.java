package com.rodrilang.librarymanager.store.order.dto;
import java.math.BigDecimal;
public record StoreOrderItemResponse(Long inventoryId, String title, String isbn, Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {}
