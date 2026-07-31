package com.rodrilang.librarymanager.integrations.tiendanube.dto.internal;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;

import java.util.List;
import java.util.Map;

public record PreviewContext(
        Map<Long, TiendanubeProductLink> linksByVariantId,
        Map<Long, TiendanubeProductLink> linksByInventoryId,
        Map<Long, Inventory> inventoriesByBookId,
        Map<String, Book> booksByIsbn,
        List<Book> books
) {
}