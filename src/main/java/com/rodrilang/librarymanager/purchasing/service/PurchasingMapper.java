package com.rodrilang.librarymanager.purchasing.service;

import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.purchasing.dto.response.*;
import com.rodrilang.librarymanager.purchasing.model.BookstoreProviderBookTerm;
import com.rodrilang.librarymanager.purchasing.model.Purchase;
import com.rodrilang.librarymanager.purchasing.model.PurchaseItem;
import org.springframework.stereotype.Component;

@Component
public class PurchasingMapper {

    public PurchaseProviderResponse toResponse(Provider provider) {
        return new PurchaseProviderResponse(
                provider.getId(),
                provider.getCode(),
                provider.getName()
        );
    }

    public ProviderBookTermResponse toResponse(BookstoreProviderBookTerm term) {
        return new ProviderBookTermResponse(
                term.getId(),
                term.getProvider().getId(),
                term.getBook().getId(),
                term.getBook().getPreferredIsbn(),
                term.getBook().getTitle(),
                term.getDiscountPercentage(),
                term.getLastPurchaseDate()
        );
    }

    public PurchaseItemResponse toResponse(PurchaseItem item) {
        return new PurchaseItemResponse(
                item.getId(),
                item.getBook().getId(),
                item.getBook().getPreferredIsbn(),
                item.getBook().getTitle(),
                item.getCondition(),
                item.getQuantity(),
                item.getEditorialPriceSnapshot(),
                item.getDiscountPercentage(),
                item.getUnitCost(),
                item.getTotalCost()
        );
    }

    public PurchaseResponse toResponse(Purchase purchase) {
        return new PurchaseResponse(
                purchase.getId(),
                toResponse(purchase.getProvider()),
                purchase.getPurchaseDate(),
                purchase.getDocumentNumber(),
                purchase.getStatus(),
                purchase.getTotalAmount(),
                purchase.getNotes(),
                purchase.getItems().stream().map(this::toResponse).toList()
        );
    }
}
