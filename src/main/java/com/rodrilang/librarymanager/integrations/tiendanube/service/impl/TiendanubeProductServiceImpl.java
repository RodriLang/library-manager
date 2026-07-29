package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateImageRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateVariantRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.*;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeMatchType;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductService;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeProductServiceImpl
        implements TiendanubeProductService {

    private final InventoryRepository inventoryRepository;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeClient client;

    @Override
    @Transactional
    public TiendanubePublishResultResponse publishInventory(Long inventoryId) {

        Inventory inventory = getInventory(inventoryId);

        TiendanubeStore store = getActiveStore(inventory.getBookstore().getId());

        validateCanPublish(inventory, store.getStoreId());

        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.PUBLISHING);

        TiendanubeCreateProductRequest request = buildCreateProductRequest(inventory);

        // TODO mejorar la creacion de la publicacion dentro de una transaccion
        TiendanubeProductResponse remoteProduct = client.createProduct(store.getStoreId(), request);

        TiendanubeVariantResponse remoteVariant = getMainVariant(remoteProduct);

        TiendanubeProductLink link = TiendanubeProductLink.builder()
                .inventory(inventory)
                .tiendanubeStoreId(store.getStoreId())
                .tiendanubeProductId(remoteProduct.id())
                .tiendanubeVariantId(remoteVariant.id())
                .sku(remoteVariant.sku())
                .active(true)
                .lastSyncedAt(Instant.now())
                .lastError(null)
                .build();

        productLinkRepository.save(link);

        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);

        log.info("Inventario publicado en Tiendanube. inventoryId={}, productId={}, variantId={}",
                inventoryId, remoteProduct.id(), remoteVariant.id());

        return new TiendanubePublishResultResponse(
                inventoryId,
                remoteProduct.id(),
                remoteVariant.id(),
                TiendanubeInventoryStatus.LINKED
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<TiendanubeRemoteProductResponse> getRemoteProducts(Long bookstoreId) {

        TiendanubeStore store = getActiveStore(bookstoreId);

        List<TiendanubeProductResponse> products = client.getProducts(store.getStoreId());

        return products.stream()
                .map(product ->
                        mapRemoteProduct(
                                bookstoreId,
                                store.getStoreId(),
                                product
                        )
                )
                .toList();
    }

    @Override
    @Transactional
    public TiendanubeProductLinkResponse linkExistingProduct(
            Long inventoryId,
            Long productId,
            Long variantId
    ) {

        Inventory inventory = getInventory(inventoryId);

        TiendanubeStore store = getActiveStore(inventory.getBookstore().getId());

        validateCanLink(inventoryId, store.getStoreId(), variantId);

        TiendanubeProductResponse remoteProduct = client.getProduct(store.getStoreId(), productId);

        TiendanubeVariantResponse remoteVariant = findVariant(remoteProduct, variantId);

        TiendanubeProductLink link = TiendanubeProductLink.builder()
                .inventory(inventory)
                .tiendanubeStoreId(store.getStoreId())
                .tiendanubeProductId(productId)
                .tiendanubeVariantId(variantId)
                .sku(remoteVariant.sku())
                .active(true)
                .build();

        productLinkRepository.save(link);

        /*
         * Library Manager es la fuente de verdad.
         * Al vincular una publicación existente,
         * sobrescribimos el stock remoto con el local.
         */
        client.updateStock(
                store.getStoreId(),
                productId,
                variantId,
                inventory.getStock()
        );

        link.setLastSyncedAt(Instant.now());
        link.setLastError(null);

        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);

        log.info("Publicación Tiendanube vinculada. inventoryId={}, productId={}, variantId={}",
                inventoryId, productId, variantId);

        return new TiendanubeProductLinkResponse(
                inventoryId,
                productId,
                variantId,
                remoteVariant.sku(),
                TiendanubeInventoryStatus.LINKED
        );
    }

    // =========================================================
    // REMOTE PRODUCTS
    // =========================================================

    private TiendanubeRemoteProductResponse mapRemoteProduct(
            Long bookstoreId,
            Long storeId,
            TiendanubeProductResponse product
    ) {

        List<TiendanubeRemoteVariantResponse> variants =
                product.variants() == null
                        ? List.of()
                        : product.variants()
                        .stream()
                        .map(variant ->
                                mapRemoteVariant(
                                        bookstoreId,
                                        storeId,
                                        product,
                                        variant
                                )
                        )
                        .toList();

        return new TiendanubeRemoteProductResponse(
                product.id(),
                getProductName(product),
                getMainImageUrl(product),
                product.published(),
                variants
        );
    }

    private TiendanubeRemoteVariantResponse mapRemoteVariant(
            Long bookstoreId,
            Long storeId,
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant
    ) {

        var existingLink = productLinkRepository.findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(
                storeId,
                variant.id()
        );

        if (existingLink.isPresent()) {

            Inventory inventory = existingLink.get().getInventory();

            return new TiendanubeRemoteVariantResponse(
                    variant.id(),
                    variant.sku(),
                    variant.barcode(),
                    variant.price(),
                    variant.stock(),
                    TiendanubeMatchType.ALREADY_LINKED,
                    inventory.getId(),
                    List.of(toCandidate(inventory))
            );
        }

        MatchResult match = findMatch(bookstoreId, product, variant);

        return new TiendanubeRemoteVariantResponse(
                variant.id(),
                variant.sku(),
                variant.barcode(),
                variant.price(),
                variant.stock(),
                match.type(),
                null,
                match.candidates()
        );
    }

    // =========================================================
    // MATCHING
    // =========================================================

    private MatchResult findMatch(
            Long bookstoreId,
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant
    ) {
        String barcode = normalizeIdentifier(variant.barcode());

        if (barcode != null) {
            List<Inventory> candidates =
                    inventoryRepository.findAllByBookstoreIdAndBookIsbn(bookstoreId, barcode);

            if (!candidates.isEmpty()) {
                return createMatchResult(
                        TiendanubeMatchType.EXACT_BARCODE,
                        candidates
                );
            }
        }

        String sku = normalizeIdentifier(variant.sku());

        if (sku != null) {
            List<Inventory> candidates =
                    inventoryRepository.findAllByBookstoreIdAndBookIsbn(bookstoreId, sku);

            if (!candidates.isEmpty()) {
                return createMatchResult(
                        TiendanubeMatchType.EXACT_SKU,
                        candidates
                );
            }
        }

        MatchResult textualMatch = findTextualMatch(bookstoreId, product);

        if (textualMatch != null) {
            return textualMatch;
        }

        return new MatchResult(
                TiendanubeMatchType.NOT_FOUND,
                List.of()
        );
    }

    private MatchResult createMatchResult(TiendanubeMatchType matchType, List<Inventory> inventories) {

        List<InventoryMatchCandidateResponse> candidates = inventories.stream()
                .map(this::toCandidate)
                .toList();

        if (candidates.size() > 1) {
            return new MatchResult(TiendanubeMatchType.MULTIPLE_MATCHES, candidates);
        }

        return new MatchResult(matchType, candidates);
    }

    private InventoryMatchCandidateResponse toCandidate(Inventory inventory) {

        Book book = inventory.getBook();

        String authors = book.getAuthors() == null
                ? null
                : book.getAuthors()
                .stream()
                .map(Author::getName)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);

        String publisher = book.getPublisher() == null
                ? null
                : book.getPublisher().getName();

        return new InventoryMatchCandidateResponse(
                inventory.getId(),
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                authors,
                publisher,
                inventory.getCondition(),
                inventory.getStock()
        );
    }

    private MatchResult findTextualMatch(
            Long bookstoreId,
            TiendanubeProductResponse product
    ) {
        String remoteName =
                TextNormalizer.normalizeForMatch(getProductName(product));

        if (remoteName.isBlank()) {
            return new MatchResult(
                    TiendanubeMatchType.NOT_FOUND,
                    List.of()
            );
        }

        List<Inventory> inventories =
                inventoryRepository.findAllByBookstoreId(bookstoreId);

        List<Inventory> titleCandidates = inventories.stream()
                .filter(inventory ->
                        matchesTitle(
                                remoteName,
                                inventory.getBook()
                        )
                )
                .toList();

        if (titleCandidates.isEmpty()) {
            return new MatchResult(
                    TiendanubeMatchType.NOT_FOUND,
                    List.of()
            );
        }

        List<Inventory> titleAndAuthorCandidates =
                titleCandidates.stream()
                        .filter(inventory ->
                                matchesAuthor(
                                        remoteName,
                                        inventory.getBook()
                                )
                        )
                        .toList();

        if (!titleAndAuthorCandidates.isEmpty()) {
            return createPossibleMatchResult(
                    titleAndAuthorCandidates
            );
        }

        return createPossibleMatchResult(
                titleCandidates
        );
    }

    private boolean matchesTitle(String remoteName, Book book) {
        String title = TextNormalizer.normalizeForMatch(book.getTitle());

        if (title.isBlank()) {
            return false;
        }

        return remoteName.equals(title)
                || remoteName.startsWith(title + " ");
    }

    private boolean matchesAuthor(String remoteName, Book book) {
        if (book.getAuthors() == null || book.getAuthors().isEmpty()) {
            return false;
        }

        return book.getAuthors().stream()
                .anyMatch(author ->
                        containsAllTokens(
                                remoteName,
                                TextNormalizer.normalizeForMatch(author.getName())
                        )
                );
    }

    private boolean containsAllTokens(String text, String candidate) {
        if (candidate.isBlank()) {
            return false;
        }

        List<String> tokens = List.of(candidate.split(" "));

        return tokens.stream()
                .filter(token -> token.length() > 1)
                .allMatch(token ->
                        List.of(text.split(" ")).contains(token)
                );
    }

    private MatchResult createPossibleMatchResult(
            List<Inventory> inventories
    ) {
        List<InventoryMatchCandidateResponse> candidates =
                inventories.stream()
                        .map(this::toCandidate)
                        .toList();

        if (candidates.size() > 1) {
            return new MatchResult(
                    TiendanubeMatchType.MULTIPLE_MATCHES,
                    candidates
            );
        }

        return new MatchResult(
                TiendanubeMatchType.POSSIBLE_MATCH,
                candidates
        );
    }

    // =========================================================
    // VALIDACIONES
    // =========================================================

    private void validateCanPublish(Inventory inventory, Long storeId) {

        if (productLinkRepository
                .findByInventoryIdAndTiendanubeStoreIdAndActiveTrue(inventory.getId(), storeId)
                .isPresent()) {

            throw new BusinessException("El inventario ya tiene una publicación vinculada en Tiendanube");
        }

        if (inventory.getStock() == null) {
            throw new BusinessException("El inventario no tiene stock definido");
        }

        if (inventory.getStock() < 0) {
            throw new BusinessException("El stock del inventario no puede ser negativo");
        }

        if (inventory.getSalePrice() == null) {
            throw new BusinessException("El inventario no tiene precio definido");
        }

        Book book =
                inventory.getBook();

        if (book.getTitle() == null || book.getTitle().isBlank()) {

            throw new BusinessException("El libro no tiene título");
        }
    }

    private void validateCanLink(
            Long inventoryId,
            Long storeId,
            Long variantId
    ) {

        if (productLinkRepository
                .findByInventoryIdAndTiendanubeStoreIdAndActiveTrue(inventoryId, storeId)
                .isPresent()) {
            throw new BusinessException("El inventario ya está vinculado con Tiendanube");
        }

        if (productLinkRepository
                .findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(storeId, variantId)
                .isPresent()) {
            throw new BusinessException("La variante de Tiendanube ya está vinculada a otro inventario");
        }
    }

    // =========================================================
    // ENTITY LOOKUPS
    // =========================================================

    private Inventory getInventory(Long inventoryId) {

        return inventoryRepository
                .findById(inventoryId)
                .orElseThrow(() -> new BusinessException("No existe el inventario con id " + inventoryId));
    }

    private TiendanubeStore getActiveStore(Long bookstoreId) {

        return storeRepository
                .findByBookstoreIdAndActiveTrue(bookstoreId)
                .orElseThrow(() -> new BusinessException("La librería no tiene una cuenta Tiendanube vinculada"));
    }

    // =========================================================
    // PUBLICACIÓN
    // =========================================================

    private TiendanubeCreateProductRequest buildCreateProductRequest(Inventory inventory) {
        Book book = inventory.getBook();

        String sku = buildSku(inventory);

        TiendanubeCreateVariantRequest variant = new TiendanubeCreateVariantRequest(
                inventory.getSalePrice(),
                inventory.getStock(),
                sku,
                book.getIsbn(),
                book.getWeightGrams(),
                book.getWidthCm(),
                book.getHeightCm(),
                book.getDepthCm()
        );

        List<TiendanubeCreateImageRequest> images =
                book.getCoverUrl() == null || book.getCoverUrl().isBlank()
                        ? List.of()
                        : List.of(new TiendanubeCreateImageRequest(book.getCoverUrl()));

        return new TiendanubeCreateProductRequest(
                Map.of("es", book.getTitle()),
                buildDescription(book),
                List.of(variant),
                images,
                true
        );
    }

    private String buildSku(Inventory inventory) {

        String isbn = normalizeIdentifier(inventory.getBook().getIsbn());

        if (isbn != null) {
            return isbn;
        }

        return "LM-" + inventory.getId();
    }

    private Map<String, String> buildDescription(Book book) {

        if (book.getDescription() == null
                || book.getDescription().isBlank()) {

            return Map.of();
        }

        return Map.of(
                "es",
                book.getDescription()
        );
    }

    // =========================================================
    // PRODUCT HELPERS
    // =========================================================

    private TiendanubeVariantResponse getMainVariant(TiendanubeProductResponse product) {

        if (product.variants() == null || product.variants().isEmpty()) {

            throw new BusinessException("Tiendanube creó el producto sin variantes");
        }

        return product.variants().getFirst();
    }

    private TiendanubeVariantResponse findVariant(
            TiendanubeProductResponse product,
            Long variantId
    ) {

        if (product.variants() == null) {
            throw new BusinessException("El producto de Tiendanube no posee variantes");
        }

        return product.variants()
                .stream()
                .filter(variant -> variant.id().equals(variantId))
                .findFirst()
                .orElseThrow(()
                        -> new BusinessException("La variante " + variantId + " no pertenece al producto " + product.id())
                );
    }

    private String normalizeIdentifier(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value
                .replace("-", "")
                .replace(" ", "")
                .trim();
    }

    // =========================================================
    // REMOTE DISPLAY
    // =========================================================

    private String getProductName(TiendanubeProductResponse product) {

        if (product.name() == null) {
            return null;
        }

        return product.name().get("es");
    }

    private String getMainImageUrl(TiendanubeProductResponse product) {

        if (product.images() == null || product.images().isEmpty()) {
            return null;
        }

        return product.images()
                .getFirst()
                .src();
    }

    // =========================================================
    // INTERNAL RECORD
    // =========================================================

    private record MatchResult(
            TiendanubeMatchType type,
            List<InventoryMatchCandidateResponse> candidates
    ) {
    }
}