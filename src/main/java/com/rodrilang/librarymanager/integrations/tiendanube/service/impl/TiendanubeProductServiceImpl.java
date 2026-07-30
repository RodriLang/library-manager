package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.internal.MatchResult;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateImageRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateVariantRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateVariantRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.*;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeMatchType;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeProductAlreadyExistsException;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeInventoryStateService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductLinkPersistenceService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeVariantSyncService;
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
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeProductServiceImpl implements TiendanubeProductService {

    private final InventoryRepository inventoryRepository;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeClient client;
    private final TiendanubeVariantSyncService variantSyncService;
    private final TiendanubeInventoryStateService inventoryStateService;
    private final TiendanubeProductLinkPersistenceService linkPersistenceService;

    @Override
    public TiendanubePublishResultResponse publishInventory(Long inventoryId) {
        Inventory inventory = getInventory(inventoryId);
        TiendanubeStore store = getActiveStore(inventory.getBookstore().getId());

        validateCanPublish(inventory, store.getStoreId());
        validateRemoteProductDoesNotExist(store.getStoreId(), inventory);

        inventoryStateService.updateStatus(inventoryId, TiendanubeInventoryStatus.PUBLISHING);

        try {
            TiendanubeCreateProductRequest request = buildCreateProductRequest(inventory);
            TiendanubeProductResponse remoteProduct = client.createProduct(store.getStoreId(), request);
            TiendanubeVariantResponse remoteVariant = getMainVariant(remoteProduct);

            linkPersistenceService.savePublishedLink(
                    inventoryId,
                    store.getStoreId(),
                    remoteProduct.id(),
                    remoteVariant
            );

            log.info("Inventario publicado en Tiendanube. inventoryId={}, productId={}, variantId={}",
                    inventoryId, remoteProduct.id(), remoteVariant.id());

            return new TiendanubePublishResultResponse(
                    inventoryId,
                    remoteProduct.id(),
                    remoteVariant.id(),
                    TiendanubeInventoryStatus.LINKED
            );

        } catch (RuntimeException exception) {
            inventoryStateService.markSyncError(inventoryId);
            log.error("Error publicando inventario en Tiendanube. inventoryId={}", inventoryId, exception);
            throw exception;
        }
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
    public TiendanubeProductLinkResponse linkExistingProduct(Long inventoryId, Long productId, Long variantId) {
        Inventory inventory = getInventory(inventoryId);

        TiendanubeStore store = getActiveStore(inventory.getBookstore().getId());

        validateCanLink(inventoryId, store.getStoreId(), variantId);

        TiendanubeProductResponse remoteProduct = client.getProduct(store.getStoreId(), productId);

        TiendanubeVariantResponse remoteVariant = findVariant(remoteProduct, variantId);

        String finalSku = updateRemoteVariantOnLink(
                store.getStoreId(),
                productId,
                remoteVariant,
                inventory
        );

        TiendanubeProductLink link = TiendanubeProductLink.builder()
                .inventory(inventory)
                .tiendanubeStoreId(store.getStoreId())
                .tiendanubeProductId(productId)
                .tiendanubeVariantId(variantId)
                .sku(finalSku)
                .active(true)
                .lastSyncedAt(Instant.now())
                .lastError(null)
                .build();

        productLinkRepository.save(link);

        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);

        log.info(
                "Publicación Tiendanube vinculada. inventoryId={}, productId={}, variantId={}",
                inventoryId,
                productId,
                variantId
        );

        return new TiendanubeProductLinkResponse(
                inventoryId,
                productId,
                variantId,
                finalSku,
                TiendanubeInventoryStatus.LINKED
        );
    }

    @Override
    public TiendanubeRetryResponse retry(Long inventoryId) {
        Inventory inventory = getInventory(inventoryId);

        if (inventory.getTiendanubeStatus() != TiendanubeInventoryStatus.SYNC_ERROR) {
            throw new BusinessException("El inventario no se encuentra en estado de error");
        }

        if (productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId).isPresent()) {
            variantSyncService.retrySync(inventoryId);

            return new TiendanubeRetryResponse(
                    inventoryId,
                    TiendanubeInventoryStatus.LINKED,
                    "SYNC"
            );
        }

        TiendanubePublishResultResponse result = publishInventory(inventoryId);

        return new TiendanubeRetryResponse(
                inventoryId,
                result.status(),
                "PUBLISH"
        );
    }

    // =========================================================
    // REMOTE PRODUCTS
    // =========================================================

    private String updateRemoteVariantOnLink(
            Long storeId,
            Long productId,
            TiendanubeVariantResponse remoteVariant,
            Inventory inventory
    ) {
        String isbn = normalizeIdentifier(inventory.getBook().getIsbn());

        boolean missingSku = remoteVariant.sku() == null || remoteVariant.sku().isBlank();
        boolean missingBarcode = remoteVariant.barcode() == null || remoteVariant.barcode().isBlank();

        String sku = missingSku && isbn != null ? isbn : remoteVariant.sku();
        String barcode = missingBarcode && isbn != null ? isbn : remoteVariant.barcode();

        TiendanubeUpdateVariantRequest request = new TiendanubeUpdateVariantRequest(
                sku,
                barcode,
                inventory.getSalePrice(),
                inventory.getStock(),
                true
        );

        client.updateVariant(storeId, productId, remoteVariant.id(), request);

        return sku;
    }

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
        if (productLinkRepository.findByInventoryIdAndTiendanubeStoreIdAndActiveTrue(inventory.getId(), storeId).isPresent()) {
            throw new BusinessException("El inventario ya tiene una publicación vinculada en Tiendanube");
        }

        TiendanubeInventoryStatus status = inventory.getTiendanubeStatus();

        if (status == TiendanubeInventoryStatus.LINKED || status == TiendanubeInventoryStatus.PUBLISHING) {
            throw new BusinessException("El inventario no puede publicarse en su estado actual: " + status);
        }

        if (inventory.getStock() == null || inventory.getStock() < 0) {
            throw new BusinessException("El inventario no tiene un stock válido");
        }

        if (inventory.getSalePrice() == null || inventory.getSalePrice().signum() <= 0) {
            throw new BusinessException("El inventario debe tener un precio de venta mayor que cero");
        }

        if (inventory.getBook().getTitle() == null || inventory.getBook().getTitle().isBlank()) {
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

    private void validateRemoteProductDoesNotExist(Long storeId, Inventory inventory) {
        String isbn = normalizeIdentifier(inventory.getBook().getIsbn());

        if (isbn == null) {
            return;
        }

        boolean exists = client.getProducts(storeId).stream()
                .flatMap(product -> product.variants() == null ? Stream.empty() : product.variants().stream())
                .anyMatch(variant ->
                        isbn.equals(normalizeIdentifier(variant.barcode()))
                                || isbn.equals(normalizeIdentifier(variant.sku()))
                );

        if (exists) {
            throw new TiendanubeProductAlreadyExistsException(
                    "Ya existe una publicación en Tiendanube para este ISBN. Debe vincularse en lugar de crear una nueva."
            );
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
        String isbn = normalizeIdentifier(book.getIsbn());

        TiendanubeCreateVariantRequest variant = new TiendanubeCreateVariantRequest(
                inventory.getSalePrice(),
                inventory.getStock(),
                sku,
                isbn,
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
}