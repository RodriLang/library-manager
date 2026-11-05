package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeCategoryResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Link one local commercial category to the authenticated bookstore's Tiendanube store.
 * Linking never changes remote categories or publications. Creating in the store
 * reuses exact sibling names and creates only missing branches.
 */
@Service
@RequiredArgsConstructor
public class CommercialCategoryRemoteLinkService {
    private final JdbcTemplate jdbc;
    private final BookstoreContext bookstoreContext;
    private final TiendanubeStoreRepository stores;
    private final TiendanubeClient client;
    private final TiendanubeCategorySyncStateService syncStates;

    public record LinkResult(Long categoryId, Long remoteCategoryId, int created, int reused) {}

    private record Local(Long id, String name, Long parentId, Long remoteId) {}

    @Transactional
    public LinkResult linkExisting(Long categoryId, Long remoteCategoryId) {
        Long bookstore = bookstoreContext.getCurrentBookstoreId();
        Long storeId = storeId(bookstore);
        Local local = local(bookstore, categoryId);
        if (remoteCategoryId == null || remoteCategoryId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccioná una categoría válida de Tiendanube");
        }
        List<TiendanubeCategoryResponse> remote = client.getCategories(storeId);
        if (remote.stream().noneMatch(c -> remoteCategoryId.equals(c.id()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoría elegida ya no existe en Tiendanube. Actualizá la lista.");
        }
        link(bookstore, local, remoteCategoryId);
        return new LinkResult(categoryId, remoteCategoryId, 0, 1);
    }

    /** Creates the missing remote ancestors and leaf, preserving the remote hierarchy. */
    @Transactional
    public LinkResult createRemote(Long categoryId) {
        Long bookstore = bookstoreContext.getCurrentBookstoreId();
        Long storeId = storeId(bookstore);
        List<Local> lineage = lineage(bookstore, categoryId);
        List<TiendanubeCategoryResponse> remote = new ArrayList<>(client.getCategories(storeId));
        Long remoteParent = null;
        int created = 0;
        int reused = 0;

        for (Local local : lineage) {
            if (local.remoteId() != null) {
                TiendanubeCategoryResponse current = remote.stream()
                        .filter(r -> Objects.equals(r.id(), local.remoteId()))
                        .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                                "Una categoría de la jerarquía está vinculada a una categoría remota que ya no existe"));
                // If a linked ancestor has a different remote path, preserve that existing remote path.
                remoteParent = current.id();
                continue;
            }
            final Long expectedParent = remoteParent;
            List<TiendanubeCategoryResponse> matches = remote.stream()
                    .filter(r -> Objects.equals(remoteParentForMatch(r), expectedParent))
                    .filter(r -> localizedName(r).strip().equalsIgnoreCase(local.name().strip()))
                    .toList();
            if (matches.size() > 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Hay varias categorías equivalentes en Tiendanube. Vinculalas manualmente.");
            }
            Long remoteId;
            if (matches.size() == 1) {
                remoteId = matches.getFirst().id();
                reused++;
            } else {
                if (local.name().length() > 150) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Tiendanube admite nombres de hasta 150 caracteres. Acortá el nombre antes de crear.");
                }
                TiendanubeCategoryResponse createdCategory = client.createCategory(storeId, local.name(), remoteParent);
                if (createdCategory == null || createdCategory.id() == null || createdCategory.id() <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Tiendanube no devolvió el ID de la categoría creada");
                }
                remote.add(createdCategory);
                remoteId = createdCategory.id();
                created++;
            }
            link(bookstore, local, remoteId);
            remoteParent = remoteId;
        }
        return new LinkResult(categoryId, remoteParent, created, reused);
    }

    public record LinkSuggestion(Long categoryId, String localPath, Long remoteCategoryId, String remotePath) {}
    public record LinkPreview(int unlinked, List<LinkSuggestion> suggestions) {}
    public record BulkLinkResult(int linked) {}

    /** Exact full-path matches only. Ambiguous paths and already mapped remote categories are excluded. */
    public LinkPreview previewExactLinks() {
        Long bookstore = bookstoreContext.getCurrentBookstoreId();
        Long store = storeId(bookstore);
        List<Local> local = allLocals(bookstore);
        List<TiendanubeCategoryResponse> remote = client.getCategories(store);
        List<LinkSuggestion> suggestions = exactSuggestions(local, remote);
        return new LinkPreview((int) local.stream().filter(c -> c.remoteId() == null).count(), suggestions);
    }

    /** Rechecks exact paths and remote mapping immediately before persisting. */
    @Transactional
    public BulkLinkResult applyExactLinks(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 200 || ids.contains(null)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccioná entre 1 y 200 categorías sin repetir");
        }
        Long bookstore = bookstoreContext.getCurrentBookstoreId();
        Long store = storeId(bookstore);
        List<Local> local = allLocals(bookstore);
        List<LinkSuggestion> suggestions = exactSuggestions(local, client.getCategories(store));
        var byLocal = new java.util.HashMap<Long, LinkSuggestion>();
        for (LinkSuggestion suggestion : suggestions) byLocal.put(suggestion.categoryId(), suggestion);
        if (!byLocal.keySet().containsAll(ids)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Las categorías cambiaron o ya no tienen una coincidencia inequívoca. Actualizá la vista previa.");
        }
        for (Long id : ids) {
            link(bookstore, local(bookstore, id), byLocal.get(id).remoteCategoryId());
        }
        return new BulkLinkResult(ids.size());
    }

    private List<Local> allLocals(Long bookstore) {
        return jdbc.query("""
                SELECT id, name, parent_id, tiendanube_category_id
                FROM bookstore_commercial_categories WHERE bookstore_id = ?
                """, (rs, row) -> new Local(rs.getLong(1), rs.getString(2),
                (Long) rs.getObject(3), (Long) rs.getObject(4)), bookstore);
    }

    private List<LinkSuggestion> exactSuggestions(List<Local> locals, List<TiendanubeCategoryResponse> remotes) {
        var localById = new java.util.HashMap<Long, Local>();
        for (Local local : locals) localById.put(local.id(), local);
        var remoteById = new java.util.HashMap<Long, TiendanubeCategoryResponse>();
        for (var remote : remotes) if (remote.id() != null) remoteById.put(remote.id(), remote);
        Set<Long> alreadyMapped = new HashSet<>();
        for (Local local : locals) if (local.remoteId() != null) alreadyMapped.add(local.remoteId());

        var remoteByPath = new java.util.HashMap<String, List<TiendanubeCategoryResponse>>();
        for (var remote : remotes) {
            if (remote.id() == null || alreadyMapped.contains(remote.id())) continue;
            String path = remotePath(remote, remoteById);
            remoteByPath.computeIfAbsent(path.toLowerCase(java.util.Locale.ROOT), k -> new ArrayList<>()).add(remote);
        }
        var localPathCounts = new java.util.HashMap<String, Integer>();
        for (Local local : locals) {
            if (local.remoteId() != null) continue;
            String path = localPath(local, localById).toLowerCase(java.util.Locale.ROOT);
            localPathCounts.merge(path, 1, Integer::sum);
        }
        List<LinkSuggestion> suggestions = new ArrayList<>();
        for (Local local : locals) {
            if (local.remoteId() != null) continue;
            String path = localPath(local, localById);
            if (localPathCounts.getOrDefault(path.toLowerCase(java.util.Locale.ROOT), 0) != 1) continue;
            List<TiendanubeCategoryResponse> matches = remoteByPath.getOrDefault(path.toLowerCase(java.util.Locale.ROOT), List.of());
            if (matches.size() == 1) {
                var remote = matches.getFirst();
                suggestions.add(new LinkSuggestion(local.id(), path, remote.id(), remotePath(remote, remoteById)));
            }
        }
        suggestions.sort(java.util.Comparator.comparing(LinkSuggestion::localPath, String.CASE_INSENSITIVE_ORDER));
        return suggestions;
    }

    private String localPath(Local item, java.util.Map<Long, Local> index) {
        List<String> names = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Local cursor = item;
        while (cursor != null && visited.add(cursor.id())) {
            names.add(cursor.name().strip());
            cursor = cursor.parentId() == null ? null : index.get(cursor.parentId());
        }
        Collections.reverse(names);
        return String.join(" / ", names);
    }

    private String remotePath(TiendanubeCategoryResponse item,
            java.util.Map<Long, TiendanubeCategoryResponse> index) {
        List<String> names = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        TiendanubeCategoryResponse cursor = item;
        while (cursor != null && cursor.id() != null && visited.add(cursor.id())) {
            names.add(localizedName(cursor).strip());
            cursor = cursor.parent() == null ? null : index.get(cursor.parent());
        }
        Collections.reverse(names);
        return String.join(" / ", names);
    }

    private Long remoteParentForMatch(TiendanubeCategoryResponse category) {
        return category.parent() == null || category.parent() == 0 ? null : category.parent();
    }

    private String localizedName(TiendanubeCategoryResponse category) {
        if (category.name() == null || category.name().isEmpty()) return "";
        return category.name().getOrDefault("es", category.name().values().iterator().next());
    }

    private void link(Long bookstore, Local local, Long remoteId) {
        if (Objects.equals(local.remoteId(), remoteId)) return;
        if (local.remoteId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría ya está vinculada a Tiendanube. No se reemplazó esa vinculación.");
        }
        Boolean taken = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM bookstore_commercial_categories
                  WHERE bookstore_id = ? AND tiendanube_category_id = ? AND id <> ?)
                """, Boolean.class, bookstore, remoteId, local.id());
        if (Boolean.TRUE.equals(taken)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta categoría de Tiendanube ya está vinculada a otra categoría local");
        }
        try {
            jdbc.update("DELETE FROM bookstore_ignored_tiendanube_categories WHERE bookstore_id = ? AND tiendanube_category_id = ?",
                    bookstore, remoteId);
            int changed = jdbc.update("""
                    UPDATE bookstore_commercial_categories
                    SET tiendanube_category_id = ?, remote_managed = FALSE, updated_at = NOW()
                    WHERE id = ? AND bookstore_id = ? AND tiendanube_category_id IS NULL
                    """, remoteId, local.id(), bookstore);
            if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría cambió durante la operación. Actualizá la pantalla.");
            List<Long> affected = jdbc.queryForList("""
                    SELECT DISTINCT inventory_id FROM inventory_commercial_categories
                    WHERE bookstore_id = ? AND category_id = ?
                    """, Long.class, bookstore, local.id());
            syncStates.localAssignmentsChanged(bookstore, affected);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La categoría remota ya fue vinculada. Actualizá la pantalla.", ex);
        }
    }

    private List<Local> lineage(Long bookstore, Long leafId) {
        List<Local> items = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Long id = leafId;
        while (id != null) {
            if (!seen.add(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La jerarquía local contiene una relación circular");
            Local item = local(bookstore, id);
            items.add(item);
            id = item.parentId();
        }
        Collections.reverse(items);
        return items;
    }

    private Local local(Long bookstore, Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría local inexistente");
        return jdbc.query("""
                SELECT id, name, parent_id, tiendanube_category_id
                FROM bookstore_commercial_categories WHERE bookstore_id = ? AND id = ?
                """, (rs, row) -> new Local(rs.getLong(1), rs.getString(2),
                (Long) rs.getObject(3), (Long) rs.getObject(4)), bookstore, id)
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría inexistente en esta librería"));
    }

    private Long storeId(Long bookstore) {
        return stores.findByBookstoreIdAndActiveTrue(bookstore)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Para vincular categorías, conectá primero Tiendanube"))
                .getStoreId();
    }
}
