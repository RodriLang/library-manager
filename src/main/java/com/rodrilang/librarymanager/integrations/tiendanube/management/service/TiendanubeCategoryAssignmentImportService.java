package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Tenant-scoped, persisted import snapshots; importing never writes to Tiendanube. */
@Service
@RequiredArgsConstructor
public class TiendanubeCategoryAssignmentImportService {
    private final JdbcTemplate jdbc;
    private final BookstoreContext bookstores;
    private final TiendanubeStoreRepository stores;
    private final TiendanubeCategoryAssignmentImportWorker worker;

    public enum Mode { EMPTY_ONLY, ADD, REPLACE }

    public record Summary(Long id, String status, String mode, int productsScanned,
                          int linkedInventories, int unlinkedProducts, int unknownCategories,
                          int processed, int changed, int skipped, int errors, String errorMessage) {}
    public record Item(Long inventoryId, String title, String remoteCategoryIds, String localCategoryIds,
                       String existingCategoryIds, String missingRemoteCategoryIds,
                       String status, String message) {}
    public record ItemsPage(long total, List<Item> items) {}

    public Summary latest() {
        Long id = jdbc.query("""
                SELECT id FROM tiendanube_category_assignment_imports
                WHERE bookstore_id=? ORDER BY id DESC LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, bookstores.getCurrentBookstoreId());
        return id == null ? null : detail(id);
    }

    public Summary start() {
        Long bookstore = bookstores.getCurrentBookstoreId();
        Long store = stores.findByBookstoreIdAndActiveTrue(bookstore)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay una tienda Tiendanube conectada"))
                .getStoreId();
        Boolean running = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM tiendanube_category_assignment_imports
                WHERE bookstore_id = ? AND status IN ('SCANNING', 'APPLYING'))
                """, Boolean.class, bookstore);
        if (Boolean.TRUE.equals(running)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe una importación de categorías en curso");
        Long id = jdbc.queryForObject("""
                INSERT INTO tiendanube_category_assignment_imports (bookstore_id, store_id)
                VALUES (?, ?) RETURNING id
                """, Long.class, bookstore, store);
        worker.scan(id, bookstore, store);
        return detail(id);
    }

    public Summary detail(Long id) {
        return jdbc.query("""
                SELECT id, status, mode, products_scanned, linked_inventories,
                       unlinked_products, unknown_categories, processed, changed,
                       skipped, errors, error_message
                FROM tiendanube_category_assignment_imports WHERE id = ? AND bookstore_id = ?
                """, rs -> rs.next() ? new Summary(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8),
                rs.getInt(9), rs.getInt(10), rs.getInt(11), rs.getString(12)) : null,
                id, bookstores.getCurrentBookstoreId());
    }

    public Summary required(Long id) {
        Summary summary = detail(id);
        if (summary == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Importación no encontrada");
        return summary;
    }

    /** Filters and pagination run in PostgreSQL; no inventory list is loaded in the browser. */
    public ItemsPage items(Long id, int page, int size, String search, String filter) {
        required(id);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginación inválida");

        String normalizedSearch = search == null ? "" : search.strip();
        if (normalizedSearch.length() > 120)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La búsqueda es demasiado larga");
        String normalizedFilter = filter == null ? "ALL" : filter.strip().toUpperCase(java.util.Locale.ROOT);
        String additionalFilter = switch (normalizedFilter) {
            case "ALL" -> "";
            case "RECOGNIZED" -> " AND ci.local_category_ids <> ''";
            case "UNRESOLVED" -> " AND ci.missing_remote_category_ids <> ''";
            case "WITHOUT_MATCH" -> " AND ci.local_category_ids = ''";
            case "CHANGED", "SKIPPED", "ERROR", "READY" -> " AND ci.status = ?";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de análisis inválido");
        };
        String searchSql = normalizedSearch.isEmpty() ? "" : """
                 AND (b.title ILIKE ? OR b.isbn_13 ILIKE ? OR b.isbn_10 ILIKE ?
                      OR CAST(ci.inventory_id AS TEXT) = ?)
                """;
        String from = """
                FROM tiendanube_category_assignment_import_items ci
                JOIN inventory i ON i.id=ci.inventory_id AND i.bookstore_id=ci.bookstore_id
                JOIN books b ON b.id=i.book_id
                WHERE ci.import_id = ? AND ci.bookstore_id = ?
                """ + additionalFilter + searchSql;
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(id);
        args.add(bookstores.getCurrentBookstoreId());
        if (java.util.Set.of("CHANGED", "SKIPPED", "ERROR", "READY").contains(normalizedFilter))
            args.add(normalizedFilter);
        if (!normalizedSearch.isEmpty()) {
            String pattern = "%" + normalizedSearch.replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(normalizedSearch);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, args.toArray());
        java.util.List<Object> pageArgs = new java.util.ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<Item> items = jdbc.query("""
                SELECT ci.inventory_id, b.title, ci.remote_category_ids, ci.local_category_ids,
                       ci.existing_category_ids, ci.missing_remote_category_ids, ci.status, ci.message
                """ + from + " ORDER BY ci.inventory_id LIMIT ? OFFSET ?",
                (rs, row) -> new Item(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)),
                pageArgs.toArray());
        return new ItemsPage(total == null ? 0 : total, items);
    }

    public Summary apply(Long id, Mode mode) {
        if (mode == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí un modo de importación");
        Long bookstore = bookstores.getCurrentBookstoreId();
        int updated = jdbc.update("""
                UPDATE tiendanube_category_assignment_imports
                SET status='APPLYING', mode=?, processed=0, changed=0, skipped=0, errors=0,
                    error_message=NULL
                WHERE id=? AND bookstore_id=? AND status='READY'
                """, mode.name(), id, bookstore);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La importación no está preparada o ya se está aplicando");
        worker.apply(id, bookstore, mode);
        return required(id);
    }
}
