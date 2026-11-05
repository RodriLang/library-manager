package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** Background scan/apply, with database progress and independent per-inventory transactions. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TiendanubeCategoryAssignmentImportWorker {
    private static final int PAGE_SIZE = 200;
    private static final int MAX_PRODUCTS = 100_000;
    private final JdbcTemplate jdbc;
    private final TiendanubeClient client;
    private final TiendanubeCategoryAssignmentImportItemWriter writer;

    @Async("tiendanubeCategoryImportExecutor")
    public void scan(Long id, Long bookstore, Long store) {
        try {
            // Local category IDs are the only IDs we persist in assignments.
            Map<Long, Long> categoryIds = new HashMap<>();
            jdbc.query("""
                SELECT tiendanube_category_id, id FROM bookstore_commercial_categories
                WHERE bookstore_id=? AND tiendanube_category_id IS NOT NULL
                """, rs -> { categoryIds.put(rs.getLong(1), rs.getLong(2)); }, bookstore);
            Map<Long, SortedSet<Long>> remoteByInventory = new TreeMap<>();
            Set<Long> unavailableInventories = new HashSet<>();
            int scanned = 0, unlinked = 0;
            for (int page = 0; ; page++) {
                var result = client.fetchProductsPage(store, page, PAGE_SIZE);
                List<TiendanubeProductResponse> products = result.products();
                if (products == null || products.isEmpty()) break;
                if (scanned + products.size() > MAX_PRODUCTS) throw new IllegalStateException(
                        "Más de 100.000 publicaciones. Contactá a soporte para importarlas por segmentos.");
                Map<Long, Set<Long>> links = productLinks(bookstore, store, products);
                for (var product : products) {
                    scanned++;
                    Set<Long> inventories = links.getOrDefault(product.id(), Set.of());
                    if (inventories.isEmpty()) { unlinked++; continue; }
                    // Do not interpret a missing categories field as "no categories":
                    // it could mean the list API omitted the field entirely.
                    if (product.categories() == null) {
                        unavailableInventories.addAll(inventories);
                    } else {
                        List<Long> remoteIds = remoteIds(product);
                        for (Long inventory : inventories) {
                            remoteByInventory.computeIfAbsent(inventory, key -> new TreeSet<>()).addAll(remoteIds);
                        }
                    }
                    for (Long inventory : inventories) {
                        remoteByInventory.computeIfAbsent(inventory, key -> new TreeSet<>());
                    }
                }
                jdbc.update("""
                        UPDATE tiendanube_category_assignment_imports
                        SET products_scanned=?, unlinked_products=?, linked_inventories=? WHERE id=?
                        """, scanned, unlinked, remoteByInventory.size(), id);
                if (products.size() < PAGE_SIZE || (result.totalPages() > 0 && page + 1 >= result.totalPages())) break;
            }
            int unknown = 0;
            for (var entry : remoteByInventory.entrySet()) {
                Long inventory = entry.getKey();
                List<Long> remote = new ArrayList<>(entry.getValue());
                List<Long> known = remote.stream().filter(categoryIds::containsKey).map(categoryIds::get).distinct().sorted().toList();
                List<Long> missing = new ArrayList<>(remote.stream().filter(r -> !categoryIds.containsKey(r)).toList());
                if (unavailableInventories.contains(inventory)) missing.add(-1L);
                List<Long> existing = jdbc.query("""
                        SELECT category_id FROM inventory_commercial_categories
                        WHERE bookstore_id=? AND inventory_id=? ORDER BY category_id
                        """, (rs, row) -> rs.getLong(1), bookstore, inventory);
                unknown += missing.size();
                jdbc.update("""
                        INSERT INTO tiendanube_category_assignment_import_items
                            (import_id, bookstore_id, inventory_id, remote_category_ids,
                             local_category_ids, existing_category_ids, missing_remote_category_ids)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, id, bookstore, inventory, csv(remote), csv(known), csv(existing), csv(missing));
            }
            jdbc.update("""
                    UPDATE tiendanube_category_assignment_imports
                    SET status='READY', linked_inventories=?, unknown_categories=?, finished_at=NOW()
                    WHERE id=?
                    """, remoteByInventory.size(), unknown, id);
        } catch (Exception ex) {
            log.error("Error analizando categorías de publicaciones Tiendanube importId={}", id, ex);
            failed(id, ex);
        }
    }

    @Async("tiendanubeCategoryImportExecutor")
    public void apply(Long id, Long bookstore, TiendanubeCategoryAssignmentImportService.Mode mode) {
        try {
            List<Long> inventoryIds = jdbc.query("""
                    SELECT inventory_id FROM tiendanube_category_assignment_import_items
                    WHERE import_id=? AND bookstore_id=? ORDER BY inventory_id
                    """, (rs, row) -> rs.getLong(1), id, bookstore);
            int processed = 0, changed = 0, skipped = 0, errors = 0;
            for (Long inventory : inventoryIds) {
                String outcome;
                try {
                    outcome = writer.applyOne(id, bookstore, inventory, mode);
                } catch (Exception ex) {
                    log.warn("No se pudo importar categorías de inventario {}", inventory, ex);
                    writer.markError(id, bookstore, inventory, ex.getMessage());
                    outcome = "ERROR";
                }
                processed++;
                if ("CHANGED".equals(outcome)) changed++;
                else if ("ERROR".equals(outcome)) errors++;
                else skipped++;
                jdbc.update("""
                        UPDATE tiendanube_category_assignment_imports
                        SET processed=?, changed=?, skipped=?, errors=? WHERE id=?
                        """, processed, changed, skipped, errors, id);
            }
            jdbc.update("""
                    UPDATE tiendanube_category_assignment_imports
                    SET status='COMPLETED', finished_at=NOW() WHERE id=?
                    """, id);
        } catch (Exception ex) {
            log.error("Error aplicando importación de categorías id={}", id, ex);
            failed(id, ex);
        }
    }

    private Map<Long, Set<Long>> productLinks(Long bookstore, Long store, List<TiendanubeProductResponse> products) {
        List<Long> ids = products.stream().map(TiendanubeProductResponse::id).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> params = new ArrayList<>(List.of(bookstore, store));
        params.addAll(ids);
        Map<Long, Set<Long>> result = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT l.tiendanube_product_id, i.id FROM tiendanube_product_links l
                JOIN inventory i ON i.id=l.inventory_id AND i.bookstore_id=?
                WHERE l.tiendanube_store_id=? AND l.active=TRUE
                  AND l.tiendanube_product_id IN (""" + placeholders + ")", rs -> {
            result.computeIfAbsent(rs.getLong(1), key -> new HashSet<>()).add(rs.getLong(2));
        }, params.toArray());
        return result;
    }

    private static List<Long> remoteIds(TiendanubeProductResponse product) {
        if (product.categories() == null) return List.of();
        Set<Long> ids = new TreeSet<>();
        for (var node : product.categories()) {
            if (node == null || node.isNull()) continue;
            Long remote = node.isNumber() ? node.longValue() :
                    node.isObject() && node.hasNonNull("id") ? node.path("id").asLong() : null;
            if (remote != null && remote > 0) ids.add(remote);
        }
        return new ArrayList<>(ids);
    }

    private static String csv(Collection<Long> ids) {
        return ids.stream().map(String::valueOf).reduce((a,b) -> a + "," + b).orElse("");
    }

    private void failed(Long id, Exception ex) {
        String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        jdbc.update("""
                UPDATE tiendanube_category_assignment_imports
                SET status='FAILED', error_message=?, finished_at=NOW() WHERE id=?
                """, msg.substring(0, Math.min(1000, msg.length())), id);
    }
}
