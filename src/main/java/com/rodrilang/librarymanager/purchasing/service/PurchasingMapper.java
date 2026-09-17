package com.rodrilang.librarymanager.purchasing.service;

import com.rodrilang.librarymanager.purchasing.dto.response.*;
import com.rodrilang.librarymanager.purchasing.model.*;
import org.springframework.stereotype.Component;

@Component
public class PurchasingMapper {
    public SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(), supplier.getName(), supplier.getTaxId(), supplier.getEmail(),
                supplier.getPhone(), supplier.getNotes(), Boolean.TRUE.equals(supplier.getActive())
        );
    }

    public BookSupplierTermResponse toResponse(BookSupplierTerm term) {
        return new BookSupplierTermResponse(
                term.getId(), term.getSupplier().getId(), term.getBook().getId(),
                term.getBook().getPreferredIsbn(), term.getBook().getTitle(),
                term.getDiscountPercentage(), term.getLastPurchaseDate()
        );
    }

    public PurchaseItemResponse toResponse(PurchaseItem item) {
        return new PurchaseItemResponse(
                item.getId(), item.getBook().getId(), item.getBook().getPreferredIsbn(), item.getBook().getTitle(),
                item.getCondition(), item.getQuantity(), item.getEditorialPriceSnapshot(),
                item.getDiscountPercentage(), item.getUnitCost(), item.getTotalCost()
        );
    }

    public PurchaseResponse toResponse(Purchase purchase) {
        return new PurchaseResponse(
                purchase.getId(), toResponse(purchase.getSupplier()), purchase.getPurchaseDate(),
                purchase.getDocumentNumber(), purchase.getStatus(), purchase.getTotalAmount(), purchase.getNotes(),
                purchase.getItems().stream().map(this::toResponse).toList()
        );
    }
}
