package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubeBulkSelectionRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubeInventoryFilterRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkSelectionType;
import com.rodrilang.librarymanager.integrations.tiendanube.management.repository.TiendanubeManagementInventoryRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TiendanubeCategoryBulkPreviewService {
    private static final int MAX_SAMPLED = 20;
    private final BookstoreContext context;
    private final TiendanubeStoreRepository stores;
    private final TiendanubeManagementInventoryRepository inventories;
    private final TiendanubeProductLinkRepository links;
    private final BookstoreCommercialCategoryService categories;
    private final TiendanubeClient client;
    private final TiendanubeCategorySyncStateService syncStates;

    public record Item(Long inventoryId, Long productId, List<Long> localCategoryIds,
                       List<Long> remoteCategoryIds, List<Long> toAdd, List<Long> toRemove,
                       String issue) {}
    public record Preview(int total, int inspected, int changed, int alreadySynced,
                          int issues, boolean partial, List<Item> items) {}

    // getProduct() writes API rate-limit state, so the surrounding transaction cannot be read-only.
    @Transactional
    public Preview preview(TiendanubeBulkSelectionRequest selection) {
        Long bookstoreId = context.getCurrentBookstoreId();
        var store = stores.findByBookstoreIdAndActiveTrue(bookstoreId)
                .filter(s -> s.isTokenValid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Tiendanube no conectada"));
        if (selection == null || selection.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la selección");
        }
        List<Long> requested;
        if (selection.type() == TiendanubeBulkSelectionType.IDS) {
            requested = selection.inventoryIds() == null ? List.of()
                    : selection.inventoryIds().stream().filter(Objects::nonNull).distinct().sorted().toList();
            if (inventories.findExistingInventoryIds(bookstoreId, new TreeSet<>(requested)).size() != requested.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La selección contiene inventarios de otra librería");
            }
        } else {
            requested = inventories.findMatchingInventoryIds(bookstoreId,
                    selection.filter() == null ? TiendanubeInventoryFilterRequest.empty() : selection.filter());
        }
        if (requested.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccioná al menos un inventario");
        }
        List<Item> items = new ArrayList<>();
        int changed = 0, alreadySynced = 0, issues = 0;
        for (Long inventoryId : requested.stream().limit(MAX_SAMPLED).toList()) {
            var link = links.findWithInventoryByInventoryIdAndTiendanubeStoreIdAndActiveTrue(inventoryId, store.getStoreId());
            if (link.isEmpty()) {
                issues++;
                items.add(new Item(inventoryId, null, List.of(), List.of(), List.of(), List.of(),
                        "Sin publicación vinculada a esta tienda"));
                continue;
            }
            long revision = syncStates.revision(bookstoreId, inventoryId, store.getStoreId());
            List<Long> local = categories.remoteCategoryIdsForInventory(bookstoreId, inventoryId);
            try {
                var product = client.getProduct(store.getStoreId(), link.get().getTiendanubeProductId());
                Set<Long> remote = new TreeSet<>();
                if (product.categories() != null) {
                    for (JsonNode node : product.categories()) {
                        if (node.isNumber()) remote.add(node.longValue());
                        else if (node.isObject() && node.hasNonNull("id")) remote.add(node.path("id").asLong());
                    }
                }
                Set<Long> desired = new TreeSet<>(local);
                List<Long> add = desired.stream().filter(id -> !remote.contains(id)).toList();
                List<Long> remove = remote.stream().filter(id -> !desired.contains(id)).toList();
                if (add.isEmpty() && remove.isEmpty()) {
                    alreadySynced++;
                    syncStates.verified(bookstoreId, inventoryId, store.getStoreId(), revision);
                } else {
                    changed++;
                    syncStates.differs(bookstoreId, inventoryId, store.getStoreId(), revision);
                }
                items.add(new Item(inventoryId, product.id(), List.copyOf(desired), List.copyOf(remote), add, remove, null));
            } catch (RuntimeException ex) {
                // Never silently claim no changes when a remote query failed.
                issues++;
                items.add(new Item(inventoryId, link.get().getTiendanubeProductId(), local,
                        List.of(), List.of(), List.of(), "No se pudo consultar la publicación"));
            }
        }
        return new Preview(requested.size(), items.size(), changed, alreadySynced, issues,
                requested.size() > items.size(), List.copyOf(items));
    }
}
