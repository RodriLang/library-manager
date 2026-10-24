package com.rodrilang.librarymanager.integrations.tiendanube.management.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateImageRequest;
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
    private final TiendanubeIgnoredPublicationService ignoredPublications;

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

    /** Detalle remoto: funciona aun cuando la publicación no esté vinculada con el inventario. */
    public TiendanubePublicationContentResponse getRemote(Long productId) {
        TiendanubeStore store = activeStore();
        TiendanubeProductResponse product = client.getProduct(store.getStoreId(), productId);
        return remoteResponse(store, product);
    }

    public TiendanubePublicationContentResponse updateRemote(Long productId, TiendanubePublicationContentRequest request) {
        TiendanubeStore store = activeStore();
        TiendanubeProductResponse original = client.getProduct(store.getStoreId(), productId);
        TiendanubePublicationContentResponse previous = remoteResponse(store, original);
        String html = buildHtml(request, previous.isbn());
        Map<String, String> localized = new LinkedHashMap<>(original.description() == null ? Map.of() : original.description());
        String locale = localized.containsKey("es") ? "es" : localized.keySet().stream().findFirst().orElse("es");
        localized.put(locale, html);
        client.updateProduct(store.getStoreId(), productId, new TiendanubeUpdateProductRequest(null, localized));
        return remoteResponse(store, client.getProduct(store.getStoreId(), productId));
    }

    /** Actualiza sólo una descripción vacía, preservando contenido HTML existente. */
    public void updateRemoteHtmlIfEmpty(Long productId, String html) {
        TiendanubeStore store = activeStore();
        var product = client.getProduct(store.getStoreId(), productId);
        boolean hasText = product.description() != null && product.description().values().stream()
            .anyMatch(value -> hasValue(value) && !value.replaceAll("(?s)<[^>]*>", "").replace("&nbsp;", " ").isBlank());
        if (hasText || !hasValue(html)) throw new ResponseStatusException(HttpStatus.CONFLICT, "La descripción ya no está vacía");
        Map<String,String> localized = new LinkedHashMap<>(product.description()==null?Map.of():product.description());
        String locale = localized.containsKey("es")?"es":localized.keySet().stream().findFirst().orElse("es");
        localized.put(locale, html);
        client.updateProduct(store.getStoreId(),productId,new TiendanubeUpdateProductRequest(null,localized));
    }

    public TiendanubePublicationContentResponse completeRemoteIdentifiers(Long productId) {
        TiendanubePublicationContentResponse publication = getRemote(productId);
        if (publication.inventoryId() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Primero vinculá esta publicación con un libro de Anaquel");
        return completeIdentifiers(publication.inventoryId());
    }

    /** Agrega una portada sin borrar las imágenes existentes. Una imagen de Anaquel exige vínculo confirmado. */
    public TiendanubePublicationContentResponse addRemoteCover(Long productId, String requestedUrl, boolean useAnaquelCover) {
        TiendanubeStore store = activeStore();
        TiendanubeProductResponse product = client.getProduct(store.getStoreId(), productId);
        TiendanubePublicationContentResponse current = remoteResponse(store, product);
        if (product.images() != null && product.images().stream().anyMatch(i -> hasValue(i.src())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La publicación ya tiene imágenes. Revisalas en Tiendanube antes de agregar otra portada.");
        String url = requestedUrl;
        if (useAnaquelCover) {
            if (current.inventoryId() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Vinculá la publicación antes de usar la portada de Anaquel");
            Context ctx = context(current.inventoryId());
            url = ctx.link().getInventory().getBook().getCoverUrl();
        }
        if (!hasValue(url) || !url.trim().matches("(?i)^https://[^\\s]+$"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresá una URL HTTPS válida para la portada");
        client.createProductImage(store.getStoreId(), productId, new TiendanubeCreateImageRequest(url.trim(), 1));
        return remoteResponse(store, client.getProduct(store.getStoreId(), productId));
    }

    private TiendanubeStore activeStore() {
        return storeRepository.findByBookstoreIdAndActiveTrue(bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Tiendanube no conectada"));
    }

    private TiendanubePublicationContentResponse remoteResponse(TiendanubeStore store, TiendanubeProductResponse product) {
        TiendanubeProductLink link = product.variants() == null ? null : product.variants().stream()
                .map(v -> linkRepository.findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(store.getStoreId(), v.id()).orElse(null))
                .filter(Objects::nonNull)
                .filter(l -> Objects.equals(l.getTiendanubeProductId(), product.id()))
                .filter(l -> Objects.equals(l.getInventory().getBookstore().getId(), bookstoreContext.getCurrentBookstoreId()))
                .findFirst().orElse(null);
        String html = product.description() == null ? "" : product.description().getOrDefault("es",
                product.description().values().stream().findFirst().orElse(""));
        TiendanubeVariantResponse variant = link == null
                ? (product.variants() == null || product.variants().isEmpty() ? null : product.variants().get(0))
                : findVariant(product, link.getTiendanubeVariantId());
        return new TiendanubePublicationContentResponse(
                link == null ? null : link.getInventory().getId(), product.id(), displayName(product),
                link == null ? null : isbn(link), html, !html.isBlank(),
                link == null ? null : link.getInventory().getBook().getDescription(),
                variant == null ? null : variant.id(), variant,
                product.images() == null ? null : product.images().stream().map(i -> i.src()).filter(TiendanubePublicationContentService::hasValue).findFirst().orElse(null),
                link == null ? null : link.getInventory().getBook().getCoverUrl());
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

    /**
     * Deletes an unlinked remote product only. Never deletes an inventory or a
     * linked product, including products with multiple variants.
     * Remote side effects cannot be rolled back with the local DB transaction.
     */
    public void deleteUnlinkedRemoteProduct(Long productId, String confirmation) {
        if (productId == null || productId <= 0 || !"DELETE".equals(confirmation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Es necesario confirmar la eliminación escribiendo DELETE");
        }
        TiendanubeStore store = activeStore();
        TiendanubeProductResponse product = client.getProduct(store.getStoreId(), productId);
        if (product.variants() == null || product.variants().isEmpty()) {
            // No variants: no inventory link can exist for the product in this integration.
        } else {
            List<Long> variantIds = product.variants().stream().map(TiendanubeVariantResponse::id)
                    .filter(Objects::nonNull).distinct().toList();
            if (!variantIds.isEmpty() && !linkRepository
                    .findAllByTiendanubeStoreIdAndTiendanubeVariantIdInAndActiveTrue(store.getId(), variantIds)
                    .isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La publicación tiene variantes vinculadas a Anaquel; primero resolvé esos vínculos");
            }
        }
        // Explicit product-id guard also catches legacy/stale variant relations.
        Integer linkedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tiendanube_product_links WHERE tiendanube_store_id = ? AND tiendanube_product_id = ? AND active = true",
                Integer.class, store.getId(), productId);
        if (linkedCount != null && linkedCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La publicación todavía tiene vínculos activos");
        }
        client.deleteProduct(store.getStoreId(), productId);
        ignoredPublications.restore(productId);
    }

    /**
     * Read-only catalog view. This response is page-limited; the Tiendanube client
     * currently fetches the complete remote collection before filtering locally.
     */
    public org.springframework.data.domain.Page<com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubePublicationQualityResponse>
    searchPublications(String query, Boolean ignoredOnly, org.springframework.data.domain.Pageable pageable) {
        TiendanubeStore store = activeStore();
        Set<Long> ignoredIds = ignoredPublications.ignoredIds(bookstoreContext.getCurrentBookstoreId());
        String term = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        List<com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubePublicationQualityResponse> results =
                client.getProducts(store.getStoreId()).stream()
                        .filter(p -> p.id() != null)
                        .filter(p -> ignoredOnly == null || ignoredIds.contains(p.id()) == ignoredOnly)
                        .filter(p -> term.isEmpty() || displayName(p).toLowerCase(java.util.Locale.ROOT).contains(term)
                                || (p.variants() != null && p.variants().stream().anyMatch(v ->
                                   (v.sku() != null && v.sku().toLowerCase(java.util.Locale.ROOT).contains(term))
                                   || (v.barcode() != null && v.barcode().toLowerCase(java.util.Locale.ROOT).contains(term)))))
                        .map(p -> {
                            boolean missingId = p.variants() == null || p.variants().isEmpty() ||
                                    p.variants().stream().noneMatch(v -> isIsbn(v.sku()) || isIsbn(v.barcode()));
                            boolean missingDescription = p.description() == null || p.description().values().stream()
                                    .noneMatch(v -> hasValue(v) && !v.replaceAll("(?s)<[^>]*>", "").replace("&nbsp;", " ").isBlank());
                            boolean missingImage = p.images() == null || p.images().stream().noneMatch(i -> hasValue(i.src()));
                            List<String> issues = new ArrayList<>();
                            if (missingId) issues.add("MISSING_IDENTIFIER");
                            if (missingDescription) issues.add("MISSING_SYNOPSIS");
                            if (missingImage) issues.add("MISSING_COVER");
                            return new com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubePublicationQualityResponse(
                                    p.id(), displayName(p), Boolean.TRUE.equals(p.published()), missingId,
                                    missingDescription, missingImage, issues);
                        }).toList();
        int from = (int) Math.min((long) results.size(), pageable.getOffset());
        int to = (int) Math.min((long) results.size(), (long) from + pageable.getPageSize());
        return new org.springframework.data.domain.PageImpl<>(results.subList(from, to), pageable, results.size());
    }

    /** Auditoría de solo lectura: incluye productos no vinculados con el inventario. */
    public List<com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubePublicationQualityResponse> auditPublicationQuality() {
        TiendanubeStore store = storeRepository.findByBookstoreIdAndActiveTrue(bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Tiendanube no conectada"));
        Set<Long> ignored = ignoredPublications.ignoredIds(bookstoreContext.getCurrentBookstoreId());
        return client.getProducts(store.getStoreId()).stream()
                .filter(product -> !ignored.contains(product.id()))
                .filter(product -> product.id() != null)
                .map(product -> {
                    boolean missingIdentifier = product.variants() == null || product.variants().isEmpty()
                            || product.variants().stream().noneMatch(variant -> isIsbn(variant.sku()) || isIsbn(variant.barcode()));
                    boolean missingSynopsis = product.description() == null || product.description().values().stream()
                            .noneMatch(value -> hasValue(value) && !value.replaceAll("(?s)<[^>]*>", "").replace("&nbsp;", " ").isBlank());
                    boolean missingCover = product.images() == null || product.images().stream()
                            .noneMatch(image -> hasValue(image.src()));
                    List<String> issues = new ArrayList<>();
                    if (missingIdentifier) issues.add("MISSING_IDENTIFIER");
                    if (missingSynopsis) issues.add("MISSING_SYNOPSIS");
                    if (missingCover) issues.add("MISSING_COVER");
                    return new com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response.TiendanubePublicationQualityResponse(
                            product.id(), displayName(product), Boolean.TRUE.equals(product.published()),
                            missingIdentifier, missingSynopsis, missingCover, issues);
                }).toList();
    }

    /** Análisis global de las publicaciones remotas; no depende de inventarios vinculados. */
    public List<TiendanubeDuplicatePairResponse> findGlobalDuplicates() {
        TiendanubeStore store = storeRepository.findByBookstoreIdAndActiveTrue(bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Tiendanube no conectada"));
        Long storeId = store.getStoreId();
        Set<String> dismissed = new HashSet<>(jdbcTemplate.query(
                "SELECT product_id_low, product_id_high FROM tiendanube_duplicate_dismissals WHERE tiendanube_store_id = ?",
                (rs, row) -> pairKey(rs.getLong(1), rs.getLong(2)), storeId));
        Set<Long> ignored = ignoredPublications.ignoredIds(bookstoreContext.getCurrentBookstoreId());
        List<TiendanubeProductResponse> products = client.getProducts(storeId).stream()
                .filter(product -> product.id() != null && !ignored.contains(product.id()))
                .toList();
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

    private static boolean isIsbn(String value) {
        if (!hasValue(value)) return false;
        String digits = value.replaceAll("[^0-9Xx]", "").toUpperCase(java.util.Locale.ROOT);
        return digits.matches("[0-9]{13}|[0-9]{9}[0-9X]");
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
                context.link().getTiendanubeVariantId(), findVariant(product, context.link().getTiendanubeVariantId()),
                product.images() == null ? null : product.images().stream().map(i -> i.src()).filter(TiendanubePublicationContentService::hasValue).findFirst().orElse(null),
                context.link().getInventory().getBook().getCoverUrl());
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
