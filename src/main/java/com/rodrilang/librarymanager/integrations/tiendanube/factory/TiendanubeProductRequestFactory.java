package com.rodrilang.librarymanager.integrations.tiendanube.factory;

import com.rodrilang.librarymanager.catalog.contribution.service.BookstoreBookEffectiveValues;
import com.rodrilang.librarymanager.catalog.contribution.service.BookstoreBookFieldOverrideService;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.*;
import com.rodrilang.librarymanager.integrations.tiendanube.util.TiendanubeProductUtils;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TiendanubeProductRequestFactory {

    private final InventoryPriceService inventoryPriceService;
    private final BookstoreBookFieldOverrideService overrideService;

    public TiendanubeCreateProductRequest createProduct(
            Inventory inventory
    ) {
        Book book = inventory.getBook();
        BookstoreBookEffectiveValues values = effectiveValues(inventory);

        String sku = buildSku(inventory);

        String isbn = TiendanubeProductUtils.normalizeIdentifier(book.getPreferredIsbn());

        TiendanubeCreateVariantRequest variant =
                new TiendanubeCreateVariantRequest(
                        inventoryPriceService.currentAmount(inventory.getId()),
                        inventory.getStock(),
                        sku,
                        isbn,
                        values.weightGrams(),
                        values.widthCm(),
                        values.heightCm(),
                        values.depthCm()
                );

        List<TiendanubeCreateImageRequest> images =
                values.coverUrl() == null || values.coverUrl().isBlank()
                        ? List.of()
                        : List.of(
                        new TiendanubeCreateImageRequest(values.coverUrl(), 1)
                );

        return new TiendanubeCreateProductRequest(
                buildName(book, values),
                buildDescription(values),
                List.of(variant),
                images,
                true
        );
    }

    public TiendanubeUpdateProductRequest updateProduct(Inventory inventory) {
        BookstoreBookEffectiveValues values = effectiveValues(inventory);

        return new TiendanubeUpdateProductRequest(
                buildName(inventory.getBook(), values),
                buildDescription(values)
        );
    }

    private Map<String, String> buildName(Book book, BookstoreBookEffectiveValues values) {

        log.info(
                "Construyendo nombre Tiendanube. bookId={}, title={}, authorsCount={}, authors={}",
                book.getId(),
                values.title(),
                values.authors() != null ? values.authors().size() : null,
                values.authors() != null
                        ? values.authors().stream().map(Author::getName).toList()
                        : null
        );

        String authorName = values.authors()
                .stream()
                .findFirst()
                .map(Author::getName)
                .orElse(null);

        String name = authorName == null || authorName.isBlank()
                ? values.title()
                : values.title() + " - " + authorName;

        log.info(
                "Nombre Tiendanube generado. bookId={}, result={}",
                book.getId(),
                name
        );

        return Map.of("es", name);
    }

    private Map<String, String> buildDescription(BookstoreBookEffectiveValues values) {
        if (values.description() == null || values.description().isBlank()) {
            return Map.of();
        }

        return Map.of("es", values.description());
    }

    private String buildSku(Inventory inventory) {

        String isbn = TiendanubeProductUtils.normalizeIdentifier(inventory.getBook().getPreferredIsbn());

        if (isbn != null) {
            return isbn;
        }

        return "LM-" + inventory.getId();
    }

    private BookstoreBookEffectiveValues effectiveValues(Inventory inventory) {
        return overrideService.resolve(
                inventory.getBook(),
                inventory.getBookstore().getId()
        );
    }
}
