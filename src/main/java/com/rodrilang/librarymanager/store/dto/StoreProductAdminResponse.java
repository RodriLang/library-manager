package com.rodrilang.librarymanager.store.dto;
import java.math.BigDecimal;
public record StoreProductAdminResponse(
        Long inventoryId,
        Long bookId,
        String title,
        String isbn,
        String coverUrl,
        Integer stock,
        BigDecimal price,
        boolean published,
        boolean featured,
        Integer featuredOrder,
        String customTitle,
        String customDescription
) {}
