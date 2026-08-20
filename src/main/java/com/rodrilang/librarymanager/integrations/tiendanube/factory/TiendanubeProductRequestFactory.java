package com.rodrilang.librarymanager.integrations.tiendanube.factory;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.*;
import com.rodrilang.librarymanager.integrations.tiendanube.util.TiendanubeProductUtils;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TiendanubeProductRequestFactory {

    public TiendanubeCreateProductRequest createProduct(
            Inventory inventory
    ) {
        Book book = inventory.getBook();

        String sku = buildSku(inventory);

        String isbn = TiendanubeProductUtils.normalizeIdentifier(book.getPreferredIsbn());

        TiendanubeCreateVariantRequest variant =
                new TiendanubeCreateVariantRequest(
                        inventory.getSalePrice(),
                        inventory.getStock(),
                        sku,
                        isbn,
                        book.getWeightGrams(),
                        book.getWidthCm(),
                        book.getHeightCm(),
                        book.getDepthCm()
                );

        List<TiendanubeCreateImageRequest> images =
                book.getCoverUrl() == null || book.getCoverUrl().isBlank()
                        ? List.of()
                        : List.of(
                        new TiendanubeCreateImageRequest(book.getCoverUrl(), 1)
                );

        return new TiendanubeCreateProductRequest(
                buildName(book),
                buildDescription(book),
                List.of(variant),
                images,
                true
        );
    }

    public TiendanubeUpdateProductRequest updateProduct(Inventory inventory) {
        Book book = inventory.getBook();

        return new TiendanubeUpdateProductRequest(
                buildName(book),
                buildDescription(book)
        );
    }

    private Map<String, String> buildName(Book book) {
        return Map.of("es", book.getTitle());
    }

    private Map<String, String> buildDescription(Book book) {
        if (book.getDescription() == null || book.getDescription().isBlank()) {
            return Map.of();
        }

        return Map.of("es", book.getDescription());
    }

    private String buildSku(Inventory inventory) {

        String isbn = TiendanubeProductUtils.normalizeIdentifier(inventory.getBook().getPreferredIsbn());

        if (isbn != null) {
            return isbn;
        }

        return "LM-" + inventory.getId();
    }
}