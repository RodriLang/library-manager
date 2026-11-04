package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Local, per-store verification state. A changing local assignment invalidates previous verification. */
@Service
@RequiredArgsConstructor
public class TiendanubeCategorySyncStateService {
    private final JdbcTemplate jdbc;

    public long revision(Long bookstoreId, Long inventoryId, Long storeId) {
        return jdbc.query("""
                SELECT revision FROM tiendanube_category_sync_states
                WHERE bookstore_id = ? AND inventory_id = ? AND store_id = ?
                """, rs -> rs.next() ? rs.getLong(1) : 0L, bookstoreId, inventoryId, storeId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void verified(Long bookstoreId, Long inventoryId, Long storeId, long expectedRevision) {
        jdbc.update("""
                INSERT INTO tiendanube_category_sync_states
                    (bookstore_id, inventory_id, store_id, state, revision, last_verified_at)
                VALUES (?, ?, ?, 'SYNCED', ?, NOW())
                ON CONFLICT (bookstore_id, inventory_id, store_id) DO UPDATE
                    SET state = 'SYNCED', last_verified_at = NOW(), changed_at = NOW()
                WHERE tiendanube_category_sync_states.revision = EXCLUDED.revision
                """, bookstoreId, inventoryId, storeId, expectedRevision);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void differs(Long bookstoreId, Long inventoryId, Long storeId, long expectedRevision) {
        jdbc.update("""
                INSERT INTO tiendanube_category_sync_states
                    (bookstore_id, inventory_id, store_id, state, revision, last_verified_at)
                VALUES (?, ?, ?, 'PENDING', ?, NOW())
                ON CONFLICT (bookstore_id, inventory_id, store_id) DO UPDATE
                    SET state = 'PENDING', last_verified_at = NOW(), changed_at = NOW()
                WHERE tiendanube_category_sync_states.revision = EXCLUDED.revision
                """, bookstoreId, inventoryId, storeId, expectedRevision);
    }

    /** Called inside the local assignment transaction so changes and invalidation commit atomically. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void localAssignmentsChanged(Long bookstoreId, Collection<Long> inventoryIds) {
        if (inventoryIds == null || inventoryIds.isEmpty()) return;
        List<Long> ids = inventoryIds.stream().filter(Objects::nonNull).distinct().toList();
        for (int offset = 0; offset < ids.size(); offset += 300) {
            List<Long> batch = ids.subList(offset, Math.min(offset + 300, ids.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            List<Object> args = new ArrayList<>();
            args.add(bookstoreId);
            args.addAll(batch);
            jdbc.update("""
                    INSERT INTO tiendanube_category_sync_states
                        (bookstore_id, inventory_id, store_id, state, revision)
                    SELECT i.bookstore_id, i.id, s.store_id, 'PENDING', 1
                    FROM inventory i
                    JOIN tiendanube_stores s ON s.bookstore_id = i.bookstore_id AND s.active = TRUE
                    JOIN tiendanube_product_links l ON l.inventory_id = i.id
                        AND l.tiendanube_store_id = s.store_id AND l.active = TRUE
                    WHERE i.bookstore_id = ? AND i.id IN (""" + placeholders + ") " + """
                    ON CONFLICT (bookstore_id, inventory_id, store_id) DO UPDATE
                        SET state = 'PENDING',
                            revision = tiendanube_category_sync_states.revision + 1,
                            changed_at = NOW()
                    """, args.toArray());
        }
    }
}
