package com.rodrilang.librarymanager.inventory.category.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkSelectionRequest;
import com.rodrilang.librarymanager.inventory.bulk.service.InventoryBulkSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommercialCategorySuggestionService {
    private static final int LIMIT = 100;
    private final JdbcTemplate jdbc;
    private final BookstoreContext context;
    private final InventoryBulkSelectionService selectionService;

    public record Suggestion(Long inventoryId, String title, Long categoryId, String path,
                             String reason, String sourceValue) {}
    public record Preview(int selected, int inspected, boolean partial, List<Suggestion> suggestions) {}
    public record Accepted(Long inventoryId, Long categoryId) {}
    public record ApplyRequest(List<Accepted> suggestions) {}
    public record Applied(int requested, int added, int alreadyAssigned) {}
    private record Category(Long id, String name, Long parentId) {}
    private record Book(Long inventoryId, String title, String categoryName, String genreName) {}

    @Transactional(readOnly = true)
    public Preview preview(InventoryBulkSelectionRequest selection) {
        List<Long> ids = selectionService.resolveIds(selection);
        List<Long> sample = ids.stream().sorted().limit(LIMIT).toList();
        return new Preview(ids.size(), sample.size(), ids.size() > LIMIT,
                recommend(sample, context.getCurrentBookstoreId()));
    }

    @Transactional
    public Applied apply(ApplyRequest request) {
        if (request == null || request.suggestions() == null || request.suggestions().isEmpty()
                || request.suggestions().size() > LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccioná entre 1 y 100 sugerencias");
        }
        Map<Long, Long> accepted = new HashMap<>();
        for (Accepted item : request.suggestions()) {
            if (item == null || item.inventoryId() == null || item.categoryId() == null
                    || accepted.putIfAbsent(item.inventoryId(), item.categoryId()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sugerencias repetidas o inválidas");
            }
        }
        Long bookstore = context.getCurrentBookstoreId();
        Map<Long, Long> actual = recommend(new ArrayList<>(accepted.keySet()), bookstore).stream()
                .collect(Collectors.toMap(Suggestion::inventoryId, Suggestion::categoryId));
        if (!accepted.equals(actual)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Las sugerencias cambiaron. Consultalas nuevamente antes de confirmar.");
        }
        int added = 0;
        for (var item : accepted.entrySet()) {
            added += jdbc.update("""
                INSERT INTO inventory_commercial_categories(bookstore_id, inventory_id, category_id)
                SELECT ?, i.id, c.id FROM inventory i
                JOIN bookstore_commercial_categories c ON c.bookstore_id = i.bookstore_id AND c.id = ?
                WHERE i.bookstore_id = ? AND i.id = ?
                ON CONFLICT DO NOTHING
                """, bookstore, item.getValue(), bookstore, item.getKey());
        }
        return new Applied(accepted.size(), added, accepted.size() - added);
    }

    private List<Suggestion> recommend(List<Long> ids, Long bookstore) {
        if (ids.isEmpty()) return List.of();
        var categories = jdbc.query("""
            SELECT id, name, parent_id FROM bookstore_commercial_categories WHERE bookstore_id = ?
            """, (rs, n) -> new Category(rs.getLong(1), rs.getString(2),
                (Long) rs.getObject(3)), bookstore);
        Map<Long, Category> byId = categories.stream().collect(Collectors.toMap(Category::id, c -> c));
        Map<String, List<Category>> names = new HashMap<>(), paths = new HashMap<>();
        for (Category c : categories) {
            names.computeIfAbsent(normalize(c.name()), k -> new ArrayList<>()).add(c);
            paths.computeIfAbsent(normalize(path(c, byId)), k -> new ArrayList<>()).add(c);
        }
        String markers = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> params = new ArrayList<>();
        params.add(bookstore);
        params.addAll(ids);
        List<Book> books = jdbc.query("""
            SELECT i.id, b.title, b.category_name, b.genre_name
            FROM inventory i JOIN books b ON b.id = i.book_id
            WHERE i.bookstore_id = ? AND i.id IN (""" + markers + ")",
            (rs, n) -> new Book(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)),
            params.toArray());
        List<Suggestion> result = new ArrayList<>();
        for (Book b : books) {
            String category = normalize(b.categoryName());
            String genre = normalize(b.genreName());
            Category chosen = null;
            String source = "", reason = "";
            if (!category.isBlank() && !genre.isBlank()) {
                chosen = unique(paths.get(category + " " + genre));
                if (chosen != null) {
                    source = b.categoryName() + " / " + b.genreName();
                    reason = "Coincidencia exacta de categoría y género";
                }
            }
            if (chosen == null && !genre.isBlank()) {
                chosen = unique(names.get(genre));
                if (chosen != null) {
                    source = b.genreName();
                    reason = "Coincidencia exacta del género";
                }
            }
            if (chosen == null && !category.isBlank()) {
                chosen = unique(names.get(category));
                if (chosen != null) {
                    source = b.categoryName();
                    reason = "Coincidencia exacta de categoría";
                }
            }
            if (chosen != null) {
                result.add(new Suggestion(b.inventoryId(), b.title(), chosen.id(),
                        path(chosen, byId), reason, source));
            }
        }
        result.sort(Comparator.comparing(Suggestion::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Suggestion::inventoryId));
        return result;
    }

    private Category unique(List<Category> categories) {
        return categories != null && categories.size() == 1 ? categories.getFirst() : null;
    }

    private static String path(Category category, Map<Long, Category> byId) {
        LinkedList<String> names = new LinkedList<>();
        Set<Long> visited = new HashSet<>();
        Category current = category;
        while (current != null && visited.add(current.id())) {
            names.addFirst(current.name());
            current = current.parentId() == null ? null : byId.get(current.parentId());
        }
        return String.join(" / ", names);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim().replaceAll("\\s+", " ");
    }
}
