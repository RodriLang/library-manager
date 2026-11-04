package com.rodrilang.librarymanager.integrations.tiendanube.job.handler.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.handler.TiendanubeJobHandler;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobConnectionGuard;
import com.rodrilang.librarymanager.integrations.tiendanube.management.service.BookstoreCommercialCategoryService;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.job.exception.TiendanubeJobExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Actualiza exclusivamente categorías; nunca stock, precio, imágenes o descripción. */
@Component
@RequiredArgsConstructor
public class TiendanubeCategoriesJobHandler implements TiendanubeJobHandler {
    private final TiendanubeJobConnectionGuard connectionGuard;
    private final TiendanubeProductLinkRepository links;
    private final BookstoreCommercialCategoryService categories;
    private final TiendanubeClient client;

    @Override
    public TiendanubeJobType type() {
        return TiendanubeJobType.SYNC_CATEGORIES;
    }

    @Override
    public void execute(TiendanubeJobExecutionContext context) {
        connectionGuard.validate(context);
        var link = links.findWithInventoryByInventoryIdAndTiendanubeStoreIdAndActiveTrue(
                context.inventoryId(), context.storeId())
                .orElseThrow(() -> TiendanubeJobExecutionException.nonRetryable(
                        "LINK_MISSING", "El inventario ya no está vinculado a esta tienda", null));
        if (!Objects.equals(link.getInventory().getBookstore().getId(), context.bookstoreId())) {
            throw TiendanubeJobExecutionException.nonRetryable(
                    "BOOKSTORE_MISMATCH", "El inventario pertenece a otra librería", null);
        }
        List<Long> desired = categories.remoteCategoryIdsForInventory(context.bookstoreId(), context.inventoryId());
        var product = client.getProduct(context.storeId(), link.getTiendanubeProductId());
        Set<Long> remote = new TreeSet<>();
        if (product.categories() != null) {
            for (JsonNode node : product.categories()) {
                Long id = node.isNumber() ? node.longValue()
                        : node.isObject() && node.hasNonNull("id") ? node.path("id").asLong() : null;
                if (id != null) remote.add(id);
            }
        }
        if (!remote.equals(new TreeSet<>(desired))) {
            client.updateProductCategories(context.storeId(), link.getTiendanubeProductId(), desired);
        }
    }
}
