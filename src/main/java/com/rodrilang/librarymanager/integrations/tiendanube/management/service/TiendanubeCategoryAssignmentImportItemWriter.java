package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** One local inventory per transaction; never requests a remote mutation. */
@Service
@RequiredArgsConstructor
public class TiendanubeCategoryAssignmentImportItemWriter {
    private final JdbcTemplate jdbc;
    private final TiendanubeCategorySyncStateService syncStates;

    private record Staged(String local, String existing, String missing) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String applyOne(Long importId, Long bookstore, Long inventoryId,
                           TiendanubeCategoryAssignmentImportService.Mode mode) {
        Staged staged = jdbc.query("""
                SELECT local_category_ids, existing_category_ids, missing_remote_category_ids
                FROM tiendanube_category_assignment_import_items
                WHERE import_id=? AND bookstore_id=? AND inventory_id=? FOR UPDATE
                """, rs -> rs.next() ? new Staged(rs.getString(1), rs.getString(2), rs.getString(3)) : null,
                importId, bookstore, inventoryId);
        if (staged == null) return "SKIPPED";
        Set<Long> expected = parse(staged.existing());
        Set<Long> incoming = parse(staged.local());
        Set<Long> missing = parse(staged.missing());
        Set<Long> current = new TreeSet<>(jdbc.query("""
                SELECT category_id FROM inventory_commercial_categories
                WHERE bookstore_id=? AND inventory_id=? ORDER BY category_id FOR UPDATE
                """, (rs, row) -> rs.getLong(1), bookstore, inventoryId));

        // A user may have changed local assignments after the remote snapshot was taken.
        if (!expected.equals(current)) return setStatus(importId, bookstore, inventoryId,
                "SKIPPED", "Las categorías cambiaron desde el análisis; revisá antes de importar.");
        if (missing.contains(-1L))
            return setStatus(importId, bookstore, inventoryId, "SKIPPED", "Tiendanube no devolvió el campo categorías; no se modificó el inventario.");
        if (mode == TiendanubeCategoryAssignmentImportService.Mode.EMPTY_ONLY && !current.isEmpty())
            return setStatus(importId, bookstore, inventoryId, "SKIPPED", "Ya tiene categorías locales.");
        if (mode == TiendanubeCategoryAssignmentImportService.Mode.REPLACE && !missing.isEmpty())
            return setStatus(importId, bookstore, inventoryId, "SKIPPED", "Hay categorías remotas sin correspondencia local.");
        if (mode != TiendanubeCategoryAssignmentImportService.Mode.REPLACE && incoming.isEmpty())
            return setStatus(importId, bookstore, inventoryId, "SKIPPED", "No hay categorías nuevas reconocidas.");

        Set<Long> desired = new TreeSet<>(incoming);
        if (mode != TiendanubeCategoryAssignmentImportService.Mode.REPLACE) desired.addAll(current);
        // Snapshot links may have been changed or removed. Verify before inserting IDs.
        if (!desired.isEmpty()) {
            String p = String.join(",", Collections.nCopies(desired.size(), "?"));
            List<Object> args = new ArrayList<>(); args.add(bookstore); args.addAll(desired);
            Long valid = jdbc.queryForObject("SELECT COUNT(*) FROM bookstore_commercial_categories WHERE bookstore_id=? AND id IN (" + p + ")",
                    Long.class, args.toArray());
            if (valid == null || valid != desired.size())
                return setStatus(importId, bookstore, inventoryId, "SKIPPED", "El árbol de categorías cambió desde el análisis.");
        }
        if (current.equals(desired))
            return setStatus(importId, bookstore, inventoryId, "SKIPPED", "Ya coincide con la selección local.");

        jdbc.update("DELETE FROM inventory_commercial_categories WHERE bookstore_id=? AND inventory_id=?", bookstore, inventoryId);
        for (Long categoryId : desired) {
            jdbc.update("""
                    INSERT INTO inventory_commercial_categories (bookstore_id, inventory_id, category_id)
                    VALUES (?,?,?) ON CONFLICT DO NOTHING
                    """, bookstore, inventoryId, categoryId);
        }
        syncStates.localAssignmentsChanged(bookstore, List.of(inventoryId));
        return setStatus(importId, bookstore, inventoryId, "CHANGED", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markError(Long importId, Long bookstore, Long inventoryId, String error) {
        setStatus(importId, bookstore, inventoryId, "ERROR", error == null ? "Error inesperado" : error);
    }

    private String setStatus(Long importId, Long bookstore, Long inventoryId, String status, String message) {
        jdbc.update("""
                UPDATE tiendanube_category_assignment_import_items
                SET status=?, message=? WHERE import_id=? AND bookstore_id=? AND inventory_id=?
                """, status, message, importId, bookstore, inventoryId);
        return status;
    }

    private static Set<Long> parse(String csv) {
        Set<Long> ids = new TreeSet<>();
        if (csv == null || csv.isBlank()) return ids;
        for (String value : csv.split(",")) ids.add(Long.parseLong(value.trim()));
        return ids;
    }
}
