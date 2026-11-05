package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Preflight for new publications. It never changes a publication or inventory assignment. */
@Service
@RequiredArgsConstructor
public class TiendanubePublicationCategoryPreparationService {
    private static final int PREVIEW_LIMIT = 100;
    private final JdbcTemplate jdbc;
    private final TiendanubeClient client;
    private final CommercialCategoryRemoteLinkService remoteLinks;

    public record Preview(int totalInventories, int inspectedInventories,
                          int inventoriesWithMissingCategories, int localCategoriesToCreate,
                          int brokenLinks, List<String> examples) {}
    private record Category(Long id, String name, Long remoteId, Long inventoryId) {}

    public Preview preview(Long bookstoreId, Long remoteStoreId, List<Long> inventoryIds) {
        List<Long> inspected = inventoryIds.stream().limit(PREVIEW_LIMIT).toList();
        var result = inspect(bookstoreId, remoteStoreId, inspected);
        Set<Long> missingIds = new HashSet<>();
        Set<Long> affected = new HashSet<>();
        int broken = 0;
        List<String> examples = new ArrayList<>();
        for (Category c : result.categories) {
            if (c.remoteId == null) {
                missingIds.add(c.id);
                affected.add(c.inventoryId);
                if (examples.size() < 8) examples.add(c.name);
            } else if (!result.remoteIds.contains(c.remoteId)) {
                broken++;
                if (examples.size() < 8) examples.add(c.name + " (vínculo roto)");
            }
        }
        return new Preview(inventoryIds.size(), inspected.size(), affected.size(),
                missingIds.size(), broken, examples.stream().distinct().toList());
    }

    /** Called before enqueueing PUBLISH. No partial queueing if preparation fails. */
    public void prepare(Long bookstoreId, Long remoteStoreId, List<Long> inventoryIds, boolean createMissing) {
        if (createMissing && inventoryIds.size() > PREVIEW_LIMIT) {
            throw new BusinessException("Para crear categorías antes de publicar, seleccioná hasta 100 inventarios por lote");
        }
        // A single snapshot of remote IDs; query local assignments in bounded batches.
        Set<Long> remoteIds = remoteIds(remoteStoreId);
        Set<Long> categoryIds = new LinkedHashSet<>();
        for (int from = 0; from < inventoryIds.size(); from += PREVIEW_LIMIT) {
            List<Category> batch = localCategories(bookstoreId,
                    inventoryIds.subList(from, Math.min(inventoryIds.size(), from + PREVIEW_LIMIT)));
            for (Category category : batch) {
                if (category.remoteId != null && !remoteIds.contains(category.remoteId)) {
                    throw new BusinessException("Hay categorías con vínculos rotos. Reparalas desde Inventario > Categorías antes de publicar");
                }
                if (category.remoteId == null) categoryIds.add(category.id);
            }
        }
        if (!createMissing) return;

        for (Long categoryId : categoryIds) {
            // createRemote reuses matching ancestors/siblings and persists their remote mappings.
            remoteLinks.createRemote(categoryId);
        }
    }

    private record Inspection(List<Category> categories, Set<Long> remoteIds) {}

    private Inspection inspect(Long bookstoreId, Long remoteStoreId, List<Long> inventoryIds) {
        return new Inspection(localCategories(bookstoreId, inventoryIds), remoteIds(remoteStoreId));
    }

    private Set<Long> remoteIds(Long remoteStoreId) {
        Set<Long> remote = new HashSet<>();
        for (var c : client.getCategories(remoteStoreId)) {
            if (c.id() != null) remote.add(c.id());
        }
        return remote;
    }

    private List<Category> localCategories(Long bookstoreId, List<Long> inventoryIds) {
        if (inventoryIds.isEmpty()) return List.of();
        // Bind each ID as an SQL parameter. IDs are validated by resolveSelection beforehand.
        String placeholders = String.join(",", java.util.Collections.nCopies(inventoryIds.size(), "?"));
        List<Object> params = new ArrayList<>();
        params.add(bookstoreId);
        params.addAll(inventoryIds);
        List<Category> categories = jdbc.query("""
            SELECT DISTINCT c.id, c.name, c.tiendanube_category_id, ic.inventory_id
            FROM inventory_commercial_categories ic
            JOIN bookstore_commercial_categories c
              ON c.id = ic.category_id AND c.bookstore_id = ic.bookstore_id
            WHERE ic.bookstore_id = ? AND ic.inventory_id IN (""" + placeholders + ")",
                (rs, n) -> new Category(rs.getLong(1), rs.getString(2),
                        (Long) rs.getObject(3), rs.getLong(4)), params.toArray());
        return categories;
    }
}
