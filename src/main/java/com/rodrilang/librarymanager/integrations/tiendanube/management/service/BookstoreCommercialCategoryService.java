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

    /** CRUD de categorías comerciales locales, independiente de la conexión con Tiendanube. */
    @Transactional
    public Category createLocal(String name, Long parentId) {
        Long bookstore = bookstoreId();
        String cleanName = validateName(name);
        validateParent(bookstore, parentId, null);
        assertNoSiblingWithName(bookstore, parentId, cleanName, null);
        Long id = jdbc.queryForObject("""
                INSERT INTO bookstore_commercial_categories (bookstore_id, name, parent_id)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, bookstore, cleanName, parentId);
        return getLocal(bookstore, id);
    }

    @Transactional
    public Category updateLocal(Long id, String name, Long parentId) {
        Long bookstore = bookstoreId();
        Category original = getLocal(bookstore, id);
        assertLocallyEditable(original);
        String cleanName = validateName(name);
        validateParent(bookstore, parentId, id);
        assertNoSiblingWithName(bookstore, parentId, cleanName, id);
        jdbc.update("""
                UPDATE bookstore_commercial_categories
                SET name = ?, parent_id = ?, updated_at = NOW()
                WHERE id = ? AND bookstore_id = ?
                """, cleanName, parentId, id, bookstore);
        return getLocal(bookstore, id);
    }

    @Transactional
    public void deleteLocal(Long id) {
        Long bookstore = bookstoreId();
        Category original = getLocal(bookstore, id);
        assertLocallyEditable(original);
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM bookstore_commercial_categories
                               WHERE bookstore_id = ? AND parent_id = ?)
                """, Boolean.class, bookstore, id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría tiene subcategorías. Movelas o eliminálas antes.");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM inventory_commercial_categories
                               WHERE bookstore_id = ? AND category_id = ?)
                """, Boolean.class, bookstore, id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría tiene libros asignados. Quitalos antes de eliminarla.");
        }
        jdbc.update("DELETE FROM bookstore_commercial_categories WHERE id = ? AND bookstore_id = ?",
                id, bookstore);
    }

    private Category getLocal(Long bookstore, Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría inexistente");
        }
        return jdbc.query("""
                SELECT id, name, parent_id, tiendanube_category_id
                FROM bookstore_commercial_categories WHERE bookstore_id = ? AND id = ?
                """, (rs, row) -> new Category(rs.getLong(1), rs.getString(2),
                        (Long) rs.getObject(3), (Long) rs.getObject(4)), bookstore, id)
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría inexistente en la librería"));
    }

    private void assertLocallyEditable(Category category) {
        if (category.tiendanubeCategoryId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta categoría está vinculada con Tiendanube. Administrala desde la integración.");
        }
    }

    private String validateName(String name) {
        String cleaned = name == null ? "" : name.strip();
        if (cleaned.isEmpty() || cleaned.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El nombre debe tener entre 1 y 255 caracteres");
        }
        return cleaned;
    }

    private void validateParent(Long bookstore, Long parentId, Long currentId) {
        if (parentId == null) return;
        getLocal(bookstore, parentId); // También valida que pertenezca a esta librería.
        if (parentId.equals(currentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Una categoría no puede ser su propia categoría padre");
        }
        if (currentId != null && Boolean.TRUE.equals(jdbc.queryForObject("""
                WITH RECURSIVE descendants AS (
                    SELECT id FROM bookstore_commercial_categories
                    WHERE bookstore_id = ? AND parent_id = ?
                    UNION
                    SELECT child.id FROM bookstore_commercial_categories child
                    JOIN descendants d ON child.parent_id = d.id
                    WHERE child.bookstore_id = ?
                )
                SELECT EXISTS (SELECT 1 FROM descendants WHERE id = ?)
                """, Boolean.class, bookstore, currentId, bookstore, parentId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No podés mover una categoría dentro de una de sus subcategorías");
        }
    }

    private void assertNoSiblingWithName(Long bookstore, Long parentId, String name, Long currentId) {
        // PostgreSQL no puede inferir el tipo de un parámetro nulo en "? IS NULL".
        // Construimos solo las condiciones aplicables y no enviamos parámetros nulos.
        StringBuilder sql = new StringBuilder("""
                SELECT EXISTS (
                    SELECT 1 FROM bookstore_commercial_categories
                    WHERE bookstore_id = ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(bookstore);

        if (parentId == null) {
            sql.append(" AND parent_id IS NULL\n");
        } else {
            sql.append(" AND parent_id = ?\n");
            parameters.add(parentId);
        }

        sql.append(" AND LOWER(name) = LOWER(?)\n");
        parameters.add(name);

        if (currentId != null) {
            sql.append(" AND id <> ?\n");
            parameters.add(currentId);
        }

        sql.append(" )");
        Boolean duplicate = jdbc.queryForObject(sql.toString(), Boolean.class, parameters.toArray());
        if (Boolean.TRUE.equals(duplicate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una categoría con ese nombre en el mismo nivel");
        }
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

    /**
     * Conserva las categorías de una publicación importada sin modificar selecciones
     * comerciales que el usuario ya haya guardado para este inventario.
     * Sólo vincula categorías ya conocidas en el árbol local de la librería.
     */
    @Transactional
    public void assignRemoteCategoriesIfEmpty(Long bookstoreId, Long inventoryId, List<Long> remoteIds) {
        if (remoteIds == null || remoteIds.isEmpty()) return;
        Boolean belongs = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM inventory WHERE id = ? AND bookstore_id = ?)",
                Boolean.class, inventoryId, bookstoreId);
        if (!Boolean.TRUE.equals(belongs)) return;
        Boolean alreadyAssigned = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM inventory_commercial_categories WHERE bookstore_id = ? AND inventory_id = ?)",
                Boolean.class, bookstoreId, inventoryId);
        if (Boolean.TRUE.equals(alreadyAssigned)) return;
        // Si la librería todavía no importó alguna categoría de la publicación,
        // se trae el árbol de Tiendanube antes de resolver sus IDs locales.
        String placeholders = String.join(",", Collections.nCopies(remoteIds.size(), "?"));
        Object[] args = new Object[remoteIds.size() + 1];
        args[0] = bookstoreId;
        for (int i = 0; i < remoteIds.size(); i++) args[i + 1] = remoteIds.get(i);
        Integer known = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT tiendanube_category_id) FROM bookstore_commercial_categories WHERE bookstore_id = ? AND tiendanube_category_id IN (" + placeholders + ")",
                Integer.class, args);
        if (known == null || known < new HashSet<>(remoteIds).size()) {
            try {
                importRemote();
            } catch (com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeApiException ex) {
                // Si falla Tiendanube, se utilizan las categorías locales conocidas.
                org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                        "No se pudo actualizar el árbol de categorías de Tiendanube", ex);
            }
        }
        for (Long remoteId : new LinkedHashSet<>(remoteIds)) {
            if (remoteId == null || remoteId <= 0) continue;
            jdbc.update("""
                INSERT INTO inventory_commercial_categories (bookstore_id, inventory_id, category_id)
                SELECT ?, ?, id FROM bookstore_commercial_categories
                WHERE bookstore_id = ? AND tiendanube_category_id = ?
                ON CONFLICT DO NOTHING
                """, bookstoreId, inventoryId, bookstoreId, remoteId);
        }
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

    public record CategoryInventory(Long inventoryId, Long bookId, String title, String isbn) {}
    public record CategoryInventoryPage(List<CategoryInventory> items, long total, int page, int size) {}

    /** Browse directly assigned inventories or the complete subtree, without changing assignments. */
    public CategoryInventoryPage inventoriesInCategory(Long categoryId, boolean includeSubcategories,
                                                        String search, int page, int size) {
        Long bookstore = bookstoreId();
        if (categoryId == null || categoryId <= 0 || !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM bookstore_commercial_categories WHERE bookstore_id = ? AND id = ?)",
                Boolean.class, bookstore, categoryId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada en esta librería");
        }
        int safePage = Math.max(0, page), safeSize = Math.max(1, Math.min(100, size));
        String trimmed = search == null ? "" : search.strip();
        String categoryCte = includeSubcategories ? """
                WITH RECURSIVE subtree AS (
                    SELECT id FROM bookstore_commercial_categories WHERE bookstore_id = ? AND id = ?
                    UNION ALL
                    SELECT child.id FROM bookstore_commercial_categories child
                    JOIN subtree parent ON child.parent_id = parent.id
                    WHERE child.bookstore_id = ?
                )
                """ : "WITH subtree AS (SELECT id FROM bookstore_commercial_categories WHERE bookstore_id = ? AND id = ?) ";
        String baseSql = """
                FROM inventory i
                JOIN books b ON b.id = i.book_id
                WHERE i.bookstore_id = ?
                  AND EXISTS (SELECT 1 FROM inventory_commercial_categories ic
                              JOIN subtree sub ON sub.id = ic.category_id
                              WHERE ic.bookstore_id = ? AND ic.inventory_id = i.id)
                  AND (? = '' OR LOWER(b.title) LIKE LOWER(?) OR LOWER(COALESCE(b.isbn_13, b.isbn_10, '')) LIKE LOWER(?))
                """;
        List<Object> params = new ArrayList<>();
        params.add(bookstore); params.add(categoryId);
        if (includeSubcategories) params.add(bookstore);
        params.add(bookstore); params.add(bookstore);
        params.add(trimmed); params.add("%" + trimmed + "%"); params.add("%" + trimmed + "%");
        Long total = jdbc.queryForObject(categoryCte + "SELECT COUNT(*) " + baseSql, Long.class, params.toArray());
        List<Object> paged = new ArrayList<>(params);
        paged.add(safeSize); paged.add(safePage * safeSize);
        List<CategoryInventory> results = jdbc.query(categoryCte +
                "SELECT i.id, b.id, b.title, COALESCE(b.isbn_13, b.isbn_10) " + baseSql + "ORDER BY b.title, i.id LIMIT ? OFFSET ?",
                (rs, n) -> new CategoryInventory(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)),
                paged.toArray());
        return new CategoryInventoryPage(results, total == null ? 0 : total, safePage, safeSize);
    }

    private void assertInventory(Long inventoryId) {
        if (inventoryId == null || inventoryId <= 0 || Boolean.FALSE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM inventory WHERE id = ? AND bookstore_id = ?)", Boolean.class, inventoryId, bookstoreId())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventario no encontrado en esta librería");
    }
    private Long bookstoreId() { return context.getCurrentBookstoreId(); }
}
