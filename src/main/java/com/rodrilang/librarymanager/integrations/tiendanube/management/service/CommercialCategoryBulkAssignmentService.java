package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkSelectionRequest;
import com.rodrilang.librarymanager.inventory.bulk.service.InventoryBulkSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Bulk local commercial classification. Never calls the Tiendanube API. */
@Service
@RequiredArgsConstructor
public class CommercialCategoryBulkAssignmentService {
    private static final int BATCH_SIZE = 300;
    private static final int MAX_INVENTORIES = 20_000;
    private final JdbcTemplate jdbc;
    private final BookstoreContext bookstoreContext;
    private final InventoryBulkSelectionService selectionService;
    private final TiendanubeCategorySyncStateService syncStates;

    public enum Mode { ADD, REPLACE, REMOVE }
    public record Request(InventoryBulkSelectionRequest selection, List<Long> categoryIds, Mode mode) {}
    public record Result(int inventories, int assignmentsAdded, int assignmentsRemoved, Mode mode) {}

    @Transactional
    public Result apply(Request request) {
        if (request == null || request.selection() == null || request.mode() == null || request.categoryIds() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar selección, categorías y operación");
        }
        List<Long> categories = request.categoryIds();
        if (categories.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(categories).size() != categories.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categorías inválidas o repetidas");
        }
        if (categories.isEmpty() && request.mode() != Mode.REPLACE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccioná al menos una categoría");
        }
        Long bookstore = bookstoreContext.getCurrentBookstoreId();
        if (!categories.isEmpty()) {
            String placeholders = placeholders(categories.size());
            List<Object> params = new ArrayList<>(); params.add(bookstore); params.addAll(categories);
            Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM bookstore_commercial_categories WHERE bookstore_id = ? AND id IN (" + placeholders + ")",
                    Integer.class, params.toArray());
            if (found == null || found != categories.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hay categorías inexistentes o de otra librería");
            }
        }
        // SelectionService checks tenancy for explicit IDs and resolves all pages for FILTER.
        List<Long> ids = selectionService.resolveIds(request.selection());
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay inventarios seleccionados");
        if (ids.size() > MAX_INVENTORIES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La operación admite hasta " + MAX_INVENTORIES + " inventarios por lote. Aplicá filtros para reducir la selección.");
        int removed = 0, added = 0;
        for (int offset = 0; offset < ids.size(); offset += BATCH_SIZE) {
            List<Long> batch = ids.subList(offset, Math.min(offset + BATCH_SIZE, ids.size()));
            List<Object> args = new ArrayList<>(); args.add(bookstore); args.addAll(batch);
            String idsClause = placeholders(batch.size());
            if (request.mode() == Mode.REPLACE) {
                removed += jdbc.update("DELETE FROM inventory_commercial_categories WHERE bookstore_id = ? AND inventory_id IN (" + idsClause + ")", args.toArray());
            } else if (request.mode() == Mode.REMOVE) {
                List<Object> removeArgs = new ArrayList<>(args); removeArgs.addAll(categories);
                removed += jdbc.update("DELETE FROM inventory_commercial_categories WHERE bookstore_id = ? AND inventory_id IN (" + idsClause + ") AND category_id IN (" + placeholders(categories.size()) + ")", removeArgs.toArray());
            }
            if (request.mode() != Mode.REMOVE) {
                for (Long categoryId : categories) {
                    // Revalidate tenancy in INSERT; ON CONFLICT makes ADD idempotent.
                    List<Object> insertArgs = new ArrayList<>();
                    insertArgs.add(bookstore); insertArgs.add(bookstore); insertArgs.add(categoryId);
                    insertArgs.add(bookstore); insertArgs.addAll(batch);
                    added += jdbc.update("""
                        INSERT INTO inventory_commercial_categories (bookstore_id, inventory_id, category_id)
                        SELECT ?, i.id, c.id FROM inventory i
                        JOIN bookstore_commercial_categories c ON c.bookstore_id = ? AND c.id = ?
                        WHERE i.bookstore_id = ? AND i.id IN (""" + idsClause + ") ON CONFLICT DO NOTHING", insertArgs.toArray());
                }
            }
        }
        if (added > 0 || removed > 0) syncStates.localAssignmentsChanged(bookstore, ids);
        return new Result(ids.size(), added, removed, request.mode());
    }

    private static String placeholders(int size) { return String.join(",", Collections.nCopies(size, "?")); }
}
