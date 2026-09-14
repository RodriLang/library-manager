package com.rodrilang.librarymanager.inventory.count.dto.internal;

import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidate;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.model.Book;

public record InventoryCountResolution(

        Book book,

        CatalogCandidate catalogCandidate,

        InventoryCountItemStatus status

) {
}
