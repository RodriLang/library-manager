package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryCategorySyncService {
    private final BookstoreContext context;
    private final TiendanubeStoreRepository stores;
    private final TiendanubeProductLinkRepository links;
    private final BookstoreCommercialCategoryService commercialCategories;
    private final TiendanubePublicationContentService publications;

    public record Comparison(Long productId, List<Long> localCategoryIds, List<Long> remoteCategoryIds,
                             List<Long> toAdd, List<Long> toRemove, boolean different) {}
    private Long productId(Long inventoryId) {
        if (inventoryId == null || inventoryId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inventario inválido");
        var store = stores.findByBookstoreIdAndActiveTrue(context.getCurrentBookstoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Tiendanube no conectada"));
        var link = links.findByInventoryIdAndTiendanubeStoreIdAndActiveTrue(inventoryId, store.getStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "El inventario no tiene una publicación vinculada"));
        if (!Objects.equals(link.getInventory().getBookstore().getId(), context.getCurrentBookstoreId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventario no encontrado");
        return link.getTiendanubeProductId();
    }
    public Comparison compare(Long inventoryId) {
        Long productId = productId(inventoryId);
        List<Long> local = commercialCategories.remoteCategoryIdsForInventory(context.getCurrentBookstoreId(), inventoryId);
        List<Long> remote = publications.publicationCategories(productId);
        Set<Long> localSet = new TreeSet<>(local);
        Set<Long> remoteSet = new TreeSet<>(remote);
        List<Long> add = localSet.stream().filter(id -> !remoteSet.contains(id)).toList();
        List<Long> remove = remoteSet.stream().filter(id -> !localSet.contains(id)).toList();
        return new Comparison(productId, List.copyOf(localSet), List.copyOf(remoteSet), add, remove, !localSet.equals(remoteSet));
    }
    /** Rechaza selecciones locales o remotas que hayan cambiado desde la vista previa. */
    public Comparison apply(Long inventoryId, List<Long> expectedLocal, List<Long> expectedRemote) {
        if (expectedLocal == null || expectedRemote == null || expectedLocal.contains(null) || expectedRemote.contains(null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la comparación previa");
        Comparison current = compare(inventoryId);
        if (!new TreeSet<>(current.localCategoryIds()).equals(new TreeSet<>(expectedLocal)) ||
                !new TreeSet<>(current.remoteCategoryIds()).equals(new TreeSet<>(expectedRemote)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Las categorías cambiaron; actualizá la comparación antes de guardar");
        if (current.different()) publications.updatePublicationCategories(current.productId(), current.localCategoryIds());
        return compare(inventoryId);
    }
}
