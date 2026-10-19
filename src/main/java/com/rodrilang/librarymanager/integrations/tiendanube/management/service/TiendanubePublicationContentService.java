package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateVariantRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request.TiendanubePublicationContentRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubePublicationContentResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubeDuplicatePairResponse;
import java.util.HashSet;
import java.util.Set;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TiendanubePublicationContentService {
    private final BookstoreContext bookstoreContext;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProductLinkRepository linkRepository;
    private final TiendanubeClient client;
    private final JdbcTemplate jdbcTemplate;

    public TiendanubePublicationContentResponse get(Long inventoryId) {
        Context context = context(inventoryId);
        return response(context, client.getProduct(context.store().getStoreId(), context.link().getTiendanubeProductId()));
    }

    public TiendanubePublicationContentResponse update(Long inventoryId, TiendanubePublicationContentRequest request) {
        Context context = context(inventoryId);
        Long storeId = context.store().getStoreId();
        Long productId = context.link().getTiendanubeProductId();
        TiendanubeProductResponse original = client.getProduct(storeId, productId);
        String isbn = isbn(context.link());
        String html = buildHtml(request, isbn);
        Map<String, String> localized = new LinkedHashMap<>(original.description() == null ? Map.of() : original.description());
        String locale = localized.containsKey("es") ? "es" : localized.keySet().stream().findFirst().orElse("es");
        localized.put(locale, html);
        client.updateProduct(storeId, productId, new TiendanubeUpdateProductRequest(null, localized));
        return response(context, client.getProduct(storeId, productId));
    }

    /** Nunca sobreescribe identificadores distintos: se deben resolver manualmente. */
    public TiendanubePublicationContentResponse completeIdentifiers(Long inventoryId) {
        Context context = context(inventoryId);
        String isbn = isbn(context.link());
        if (isbn == null || isbn.isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El libro de Anaquel no tiene ISBN");
        Long storeId = context.store().getStoreId();
        Long productId = context.link().getTiendanubeProductId();
        TiendanubeProductResponse product = client.getProduct(storeId, productId);
        TiendanubeVariantResponse variant = product.variants() == null ? null : product.variants().stream()
                .filter(v -> Objects.equals(v.id(), context.link().getTiendanubeVariantId())).findFirst().orElse(null);
        if (variant == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "La variante vinculada ya no existe en Tiendanube");
        if ((hasValue(variant.sku()) && !isbn.equals(variant.sku().trim())) ||
            (hasValue(variant.barcode()) && !isbn.equals(variant.barcode().trim())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El SKU o código de barras ya contiene un valor diferente; revisalo antes de reemplazarlo");
        if (!isbn.equals(variant.sku()) || !isbn.equals(variant.barcode())) {
            client.updateVariant(storeId, productId, variant.id(), new TiendanubeUpdateVariantRequest(
                    isbn, isbn, null, null, null, null, null, null, null));
        }
        return response(context, client.getProduct(storeId, productId));
    }

    /** Análisis global de las publicaciones remotas; no depende de inventarios vinculados. */
    public List<TiendanubeDuplicatePairResponse> findGlobalDuplicates() {
        TiendanubeStore store = storeRepository.findByBookstoreIdAndActiveTrue(bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Tiendanube no conectada"));
        Long storeId = store.getStoreId();
        Set<String> dismissed = new HashSet<>(jdbcTemplate.query(
                "SELECT product_id_low, product_id_high FROM tiendanube_duplicate_dismissals WHERE tiendanube_store_id = ?",
                (rs, row) -> pairKey(rs.getLong(1), rs.getLong(2)), storeId));
        List<TiendanubeProductResponse> products = client.getProducts(storeId);
        Map<String, List<TiendanubeProductResponse>> byIsbn = new LinkedHashMap<>();
        Map<String, List<TiendanubeProductResponse>> byTitle = new LinkedHashMap<>();
        for (TiendanubeProductResponse product : products) {
            if (product.id() == null) continue;
            if (product.variants() != null) for (TiendanubeVariantResponse variant : product.variants()) {
                for (String identifier : List.of(variant.sku() == null ? "" : variant.sku(), variant.barcode() == null ? "" : variant.barcode())) {
                    String key = identifier.replaceAll("[^0-9Xx]", "").toUpperCase(java.util.Locale.ROOT);
                    if (key.matches("(?:[0-9]{13}|[0-9]{9}[0-9X])")) byIsbn.computeIfAbsent(key, k -> new ArrayList<>()).add(product);
                }
            }
            String title = normalizedName(product);
            if (!title.isBlank()) byTitle.computeIfAbsent(title, k -> new ArrayList<>()).add(product);
        }
        Map<String, TiendanubeDuplicatePairResponse> matches = new LinkedHashMap<>();
        appendPairs(matches, dismissed, byTitle, "SAME_TITLE");
        appendPairs(matches, dismissed, byIsbn, "SAME_ISBN"); // ISBN exacto prevalece
        return new ArrayList<>(matches.values());
    }

    private static void appendPairs(Map<String, TiendanubeDuplicatePairResponse> matches, Set<String> dismissed,
                                    Map<String, List<TiendanubeProductResponse>> groups, String reason) {
        for (List<TiendanubeProductResponse> items : groups.values()) {
            for (int i = 0; i < items.size(); i++) for (int j = i + 1; j < items.size(); j++) {
                TiendanubeProductResponse a = items.get(i), b = items.get(j);
                if (a.id().equals(b.id())) continue;
                String key = pairKey(a.id(), b.id());
                if (!dismissed.contains(key)) matches.put(key, new TiendanubeDuplicatePairResponse(
                        a.id(), displayName(a), b.id(), displayName(b), reason));
            }
        }
    }

    private static String displayName(TiendanubeProductResponse product) {
        return product.name() == null || product.name().isEmpty() ? "Producto #" + product.id()
                : product.name().getOrDefault("es", product.name().values().iterator().next());
    }

    private static String pairKey(Long a, Long b) { return Math.min(a, b) + ":" + Math.max(a, b); }

    public void dismissDuplicate(Long first, Long second) {
        if (first == null || second == null || first.equals(second) || first <= 0 || second <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccioná dos publicaciones diferentes");
        TiendanubeStore store = storeRepository.findByBookstoreIdAndActiveTrue(bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Tiendanube no conectada"));
        // Se valida contra el catálogo remoto actual para evitar guardar exclusiones arbitrarias.
        Set<Long> ids = new HashSet<>();
        for (TiendanubeProductResponse p : client.getProducts(store.getStoreId())) ids.add(p.id());
        if (!ids.contains(first) || !ids.contains(second))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alguna publicación ya no existe en Tiendanube");
        jdbcTemplate.update("INSERT INTO tiendanube_duplicate_dismissals(tiendanube_store_id, product_id_low, product_id_high) "
                + "VALUES (?, ?, ?) ON CONFLICT (tiendanube_store_id, product_id_low, product_id_high) DO NOTHING",
                store.getStoreId(), Math.min(first, second), Math.max(first, second));
    }

    private static boolean hasValue(String text) { return text != null && !text.isBlank(); }
    private static String normalizedName(TiendanubeProductResponse p) {
        if (p.name() == null || p.name().isEmpty()) return "";
        String text = p.name().getOrDefault("es", p.name().values().iterator().next());
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^\\p{Alnum}]+", " ").trim().toLowerCase(java.util.Locale.ROOT);
    }

    private Context context(Long inventoryId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        TiendanubeStore store = storeRepository.findByBookstoreIdAndActiveTrue(bookstoreId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Tiendanube no conectada"));
        TiendanubeProductLink link = linkRepository.findWithInventoryBookByInventoryIdAndActiveTrue(inventoryId)
                .filter(l -> l.getInventory().getBookstore().getId().equals(bookstoreId))
                .filter(l -> l.getTiendanubeStoreId().equals(store.getStoreId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no vinculada"));
        return new Context(store, link);
    }

    private TiendanubePublicationContentResponse response(Context context, TiendanubeProductResponse product) {
        String html = product.description() == null ? "" : product.description().getOrDefault("es",
                product.description().values().stream().findFirst().orElse(""));
        return new TiendanubePublicationContentResponse(
                context.link().getInventory().getId(), context.link().getTiendanubeProductId(),
                context.link().getInventory().getBook().getTitle(),
                isbn(context.link()), html, !html.isBlank(),
                context.link().getInventory().getBook().getDescription(),
                context.link().getTiendanubeVariantId(), findVariant(product, context.link().getTiendanubeVariantId()));
    }

    private static TiendanubeVariantResponse findVariant(TiendanubeProductResponse product, Long variantId) {
        return product.variants() == null ? null : product.variants().stream()
                .filter(v -> Objects.equals(v.id(), variantId)).findFirst().orElse(null);
    }

    /** Escapa la entrada para que los textos de librería nunca inyecten marcado arbitrario. */
    private static String isbn(TiendanubeProductLink link) {
        String isbn13 = link.getInventory().getBook().getIsbn13();
        return isbn13 != null && !isbn13.isBlank() ? isbn13 : link.getInventory().getBook().getIsbn10();
    }

    private static String buildHtml(TiendanubePublicationContentRequest input, String isbn) {
        StringBuilder html = new StringBuilder();
        if (!input.synopsis().isBlank()) html.append("<p>").append(escape(input.synopsis())).append("</p>");
        if (!input.commercialMessage().isBlank()) html.append("<p><strong>").append(escape(input.commercialMessage())).append("</strong></p>");
        if (input.includeIsbn() && isbn != null && !isbn.isBlank())
            html.append("<p>ISBN: ").append(escape(isbn)).append("</p>");
        return html.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;").replace("\r\n", "\n")
                .replace("\n", "<br>");
    }

    private record Context(TiendanubeStore store, TiendanubeProductLink link) {}
}
