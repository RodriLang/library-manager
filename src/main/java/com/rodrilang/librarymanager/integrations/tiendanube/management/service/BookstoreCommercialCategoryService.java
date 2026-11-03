package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeCategoryResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class BookstoreCommercialCategoryService {
    private final JdbcTemplate jdbc;
    private final BookstoreContext context;
    private final TiendanubeStoreRepository stores;
    private final TiendanubeClient client;

    public record Category(Long id, String name, Long parentId, Long tiendanubeCategoryId) {}

    public List<Category> list() {
        return jdbc.query("SELECT id, name, parent_id, tiendanube_category_id FROM bookstore_commercial_categories WHERE bookstore_id = ? ORDER BY name, id",
                (rs, i) -> new Category(rs.getLong(1), rs.getString(2), (Long)rs.getObject(3), (Long)rs.getObject(4)), bookstoreId());
    }

    /** Importación de la jerarquía, sin borrar categorías locales ni asignaciones existentes. */
    @Transactional
    public List<Category> importRemote() {
        Long bookstore = bookstoreId();
        var store = stores.findByBookstoreIdAndActiveTrue(bookstore).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Tiendanube no conectada"));
        List<TiendanubeCategoryResponse> remote = client.getCategories(store.getStoreId());
        // Primera pasada: todas las categorías existen antes de asignar padres.
        for (var r : remote) {
            if (r.id() == null || r.id() <= 0) continue;
            String name = r.name() == null ? "Categoría #" + r.id() :
                    r.name().getOrDefault("es", r.name().values().stream().findFirst().orElse("Categoría #" + r.id()));
            jdbc.update("""
                INSERT INTO bookstore_commercial_categories (bookstore_id, name, tiendanube_category_id)
                VALUES (?, ?, ?) ON CONFLICT (bookstore_id, tiendanube_category_id)
                DO UPDATE SET name = EXCLUDED.name, updated_at = NOW()
                """, bookstore, name, r.id());
        }
        // Segunda pasada: se resuelven relaciones por IDs remotos (sin depender del orden).
        for (var r : remote) {
            if (r.id() == null) continue;
            jdbc.update("""
                UPDATE bookstore_commercial_categories AS child SET parent_id = (
                    SELECT parent.id FROM bookstore_commercial_categories parent
                    WHERE parent.bookstore_id = ? AND parent.tiendanube_category_id = ?
                ), updated_at = NOW()
                WHERE child.bookstore_id = ? AND child.tiendanube_category_id = ?
                """, bookstore, r.parent(), bookstore, r.id());
        }
        return list();
    }

    @Transactional
    public List<Long> categoriesForInventory(Long inventoryId) {
        assertInventory(inventoryId);
        return jdbc.query("SELECT category_id FROM inventory_commercial_categories WHERE bookstore_id = ? AND inventory_id = ? ORDER BY category_id",
                (rs, i) -> rs.getLong(1), bookstoreId(), inventoryId);
    }

    @Transactional
    public List<Long> assignToInventory(Long inventoryId, List<Long> categoryIds) {
        assertInventory(inventoryId);
        if (categoryIds == null || categoryIds.stream().anyMatch(Objects::isNull) || new HashSet<>(categoryIds).size() != categoryIds.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categorías inválidas o repetidas");
        Long bookstore = bookstoreId();
        if (!categoryIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(categoryIds.size(), "?"));
            Object[] params = new Object[categoryIds.size() + 1]; params[0] = bookstore;
            for (int i = 0; i < categoryIds.size(); i++) params[i + 1] = categoryIds.get(i);
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM bookstore_commercial_categories WHERE bookstore_id = ? AND id IN (" + placeholders + ")", Integer.class, params);
            if (count == null || count != categoryIds.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categorías ajenas a la librería o inexistentes");
        }
        jdbc.update("DELETE FROM inventory_commercial_categories WHERE bookstore_id = ? AND inventory_id = ?", bookstore, inventoryId);
        for (Long id : categoryIds) jdbc.update("INSERT INTO inventory_commercial_categories (bookstore_id, inventory_id, category_id) VALUES (?, ?, ?)", bookstore, inventoryId, id);
        return categoriesForInventory(inventoryId);
    }

    /** Only mapped categories are sent to Tiendanube; no mapping means leave remote categories unspecified. */
    public List<Long> remoteCategoryIdsForInventory(Long bookstoreId, Long inventoryId) {
        return jdbc.query("""
                SELECT DISTINCT c.tiendanube_category_id
                FROM inventory_commercial_categories ic
                JOIN bookstore_commercial_categories c ON c.id = ic.category_id AND c.bookstore_id = ic.bookstore_id
                WHERE ic.bookstore_id = ? AND ic.inventory_id = ?
                  AND c.tiendanube_category_id IS NOT NULL
                ORDER BY c.tiendanube_category_id
                """, (rs, i) -> rs.getLong(1), bookstoreId, inventoryId);
    }

    private void assertInventory(Long inventoryId) {
        if (inventoryId == null || inventoryId <= 0 || Boolean.FALSE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM inventory WHERE id = ? AND bookstore_id = ?)", Boolean.class, inventoryId, bookstoreId())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventario no encontrado en esta librería");
    }
    private Long bookstoreId() { return context.getCurrentBookstoreId(); }
}
