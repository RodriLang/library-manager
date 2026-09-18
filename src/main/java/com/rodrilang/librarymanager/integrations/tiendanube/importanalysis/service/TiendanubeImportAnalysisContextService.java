package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisMatchContext;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TiendanubeImportAnalysisContextService {

    private final InventoryRepository inventoryRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final IsbnService isbnService;

    @Transactional(readOnly = true)
    public TiendanubeImportAnalysisMatchContext build(
            Long bookstoreId,
            Long storeId,
            List<TiendanubeProductResponse> products
    ) {
        List<Inventory> inventories = inventoryRepository.findAllForTiendanubeMatching(
                bookstoreId,
                BookCondition.NEW
        );

        return new TiendanubeImportAnalysisMatchContext(
                inventories,
                indexInventoriesByIsbn(inventories),
                loadLinksByVariantId(storeId, products),
                loadLinksByInventoryId(storeId, inventories)
        );
    }

    public List<Inventory> findInventoriesByIdentifier(
            TiendanubeImportAnalysisMatchContext context,
            ParsedIsbn parsed
    ) {
        Map<Long, Inventory> unique = new LinkedHashMap<>();

        Stream.of(parsed.isbn13(), parsed.isbn10())
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> context.inventoriesByIsbn().getOrDefault(value, List.of()).stream())
                .forEach(inventory -> unique.put(inventory.getId(), inventory));

        return new ArrayList<>(unique.values());
    }

    private Map<String, List<Inventory>> indexInventoriesByIsbn(List<Inventory> inventories) {
        Map<String, List<Inventory>> result = new HashMap<>();

        for (Inventory inventory : inventories) {
            Book book = inventory.getBook();
            addInventoryIndex(result, book.getIsbn13(), inventory);
            addInventoryIndex(result, book.getIsbn10(), inventory);

            ParsedIsbn parsed = isbnService.parse(book.getPreferredIsbn());
            if (parsed.valid()) {
                addInventoryIndex(result, parsed.isbn13(), inventory);
                addInventoryIndex(result, parsed.isbn10(), inventory);
            }
        }

        return result;
    }

    private void addInventoryIndex(
            Map<String, List<Inventory>> index,
            String isbn,
            Inventory inventory
    ) {
        String normalized = isbnService.normalize(isbn);

        if (normalized == null) {
            return;
        }

        index.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(inventory);
    }

    private Map<Long, TiendanubeProductLink> loadLinksByVariantId(
            Long storeId,
            List<TiendanubeProductResponse> products
    ) {
        List<Long> variantIds = products.stream()
                .flatMap(product -> product.variants() == null
                        ? Stream.empty()
                        : product.variants().stream())
                .map(TiendanubeVariantResponse::id)
                .distinct()
                .toList();

        if (variantIds.isEmpty()) {
            return Map.of();
        }

        return productLinkRepository
                .findAllByTiendanubeStoreIdAndTiendanubeVariantIdInAndActiveTrue(storeId, variantIds)
                .stream()
                .collect(Collectors.toMap(
                        TiendanubeProductLink::getTiendanubeVariantId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private Map<Long, TiendanubeProductLink> loadLinksByInventoryId(
            Long storeId,
            List<Inventory> inventories
    ) {
        List<Long> inventoryIds = inventories.stream()
                .map(Inventory::getId)
                .toList();

        if (inventoryIds.isEmpty()) {
            return Map.of();
        }

        return productLinkRepository.findAllByInventoryIdInAndActiveTrue(inventoryIds).stream()
                .filter(link -> storeId.equals(link.getTiendanubeStoreId()))
                .collect(Collectors.toMap(
                        link -> link.getInventory().getId(),
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }
}
