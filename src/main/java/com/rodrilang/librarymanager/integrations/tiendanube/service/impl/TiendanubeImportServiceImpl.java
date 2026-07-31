package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.internal.PreviewContext;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.internal.PreviewMatch;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.internal.TiendanubeImportCommand;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeBulkImportRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeImportItemRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeBulkImportResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImportBookCandidateResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImportItemResultResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImportPreviewItemResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImportPreviewResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImportResultResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductLinkResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductsPage;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeImportAction;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeImportMatchType;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeImportPersistenceService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeImportService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductLinkService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductMatchingService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductSyncService;
import com.rodrilang.librarymanager.integrations.tiendanube.util.TiendanubeProductUtils;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeImportServiceImpl implements TiendanubeImportService {

    private final BookRepository bookRepository;
    private final InventoryRepository inventoryRepository;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeClient client;
    private final TiendanubeImportPersistenceService importPersistenceService;
    private final TiendanubeProductLinkService productLinkService;
    private final TiendanubeProductMatchingService matchingService;
    private final TiendanubeProductSyncService productSyncService;

    @Override
    public TiendanubeImportPreviewResponse preview(
            Long bookstoreId,
            int page,
            int size
    ) {
        TiendanubeStore store = getActiveStore(bookstoreId);

        TiendanubeProductsPage productsPage =
                client.fetchProductsPage(
                        store.getStoreId(),
                        page,
                        size
                );

        PreviewContext context =
                buildPreviewContext(
                        bookstoreId,
                        store.getStoreId()
                );

        List<TiendanubeImportPreviewItemResponse> items =
                productsPage.products()
                        .stream()
                        .flatMap(product ->
                                buildPreviewItems(product, context).stream()
                        )
                        .toList();

        return buildPreviewResponse(
                items,
                productsPage
        );
    }

    @Override
    public TiendanubeImportResultResponse importProduct(Long bookstoreId, Long productId, Long variantId) {
        TiendanubeStore store = getActiveStore(bookstoreId);

        validateVariantNotLinked(store.getStoreId(), variantId);

        TiendanubeProductResponse product = client.getProduct(store.getStoreId(), productId);
        TiendanubeVariantResponse variant = TiendanubeProductUtils.findVariant(product, variantId);
        String isbn = TiendanubeProductUtils.resolveRemoteIsbn(variant);

        if (isbn == null) {
            throw new BusinessException("La publicación no tiene ISBN. Debe asociarse manualmente a un libro existente.");
        }

        Book book = bookRepository.findByIsbn(isbn)
                .orElseThrow(() -> new BusinessException("No existe un libro en el catálogo con ISBN " + isbn));

        Optional<Inventory> existingInventory = inventoryRepository.findWithBookDetailsByBookIdAndBookstoreIdAndCondition(
                book.getId(),
                bookstoreId,
                BookCondition.NEW
        );

        if (existingInventory.isPresent()) {
            throw new BusinessException("El libro ya existe en el inventario. Debe vincularse la publicación existente.");
        }

        if (variant.price() == null || variant.price().signum() <= 0) {
            throw new BusinessException("La publicación no tiene un precio válido. Debe corregirse antes de importarla.");
        }

        Integer stock = variant.stock() != null ? variant.stock() : 0;

        TiendanubeImportCommand command = new TiendanubeImportCommand(
                bookstoreId,
                book.getId(),
                store.getStoreId(),
                BookCondition.NEW,
                stock,
                variant.price(),
                true,
                false
        );

        TiendanubeImportResultResponse result = importPersistenceService.importExistingBook(
                command,
                product,
                variant
        );

        productSyncService.syncAfterImport(result.inventoryId());

        return result;
    }

    @Override
    public TiendanubeBulkImportResponse importProducts(Long bookstoreId, TiendanubeBulkImportRequest request) {
        TiendanubeStore store = getActiveStore(bookstoreId);
        List<TiendanubeImportItemResultResponse> results = new ArrayList<>();

        for (TiendanubeImportItemRequest item : request.items()) {
            results.add(importItem(bookstoreId, store, item));
        }

        int imported = (int) results.stream()
                .filter(result -> result.success() && result.action() == TiendanubeImportAction.IMPORTED)
                .count();

        int linked = (int) results.stream()
                .filter(result -> result.success() && result.action() == TiendanubeImportAction.LINKED)
                .count();

        int failed = (int) results.stream()
                .filter(result -> !result.success())
                .count();

        return new TiendanubeBulkImportResponse(
                request.items().size(),
                imported,
                linked,
                failed,
                results
        );
    }

    private PreviewContext buildPreviewContext(Long bookstoreId, Long storeId) {

        List<TiendanubeProductLink> activeLinks =
                productLinkRepository.findAllByTiendanubeStoreIdAndActiveTrue(storeId);

        Map<Long, TiendanubeProductLink> linksByVariantId = activeLinks.stream()
                .collect(Collectors.toMap(
                        TiendanubeProductLink::getTiendanubeVariantId,
                        Function.identity()
                ));

        Map<Long, TiendanubeProductLink> linksByInventoryId = activeLinks.stream()
                .collect(Collectors.toMap(
                        link -> link.getInventory().getId(),
                        Function.identity(),
                        (first, second) -> first
                ));

        // TODO: Reemplazar Map<bookId, Inventory> cuando Tiendanube soporte importación por condición (NEW/USED).
        Map<Long, Inventory> inventoriesByBookId =
                inventoryRepository.findAllByBookstoreId(bookstoreId)
                        .stream()
                        .filter(inventory ->
                                inventory.getCondition() == BookCondition.NEW
                        )
                        .collect(Collectors.toMap(
                                inventory -> inventory.getBook().getId(),
                                Function.identity()
                        ));

        List<Book> books = bookRepository.findAllByActiveTrue();

        Map<String, Book> booksByIsbn = books.stream()
                .filter(book ->
                        TiendanubeProductUtils.normalizeIdentifier(book.getIsbn()) != null
                )
                .collect(Collectors.toMap(
                        book -> TiendanubeProductUtils.normalizeIdentifier(book.getIsbn()),
                        Function.identity(),
                        (first, second) -> first
                ));

        return new PreviewContext(
                linksByVariantId,
                linksByInventoryId,
                inventoriesByBookId,
                booksByIsbn,
                books
        );
    }

    private TiendanubeImportItemResultResponse importItem(
            Long bookstoreId,
            TiendanubeStore store,
            TiendanubeImportItemRequest item
    ) {
        try {
            validateVariantNotLinked(store.getStoreId(), item.variantId());

            TiendanubeProductResponse product = client.getProduct(store.getStoreId(), item.productId());
            TiendanubeVariantResponse variant = TiendanubeProductUtils.findVariant(product, item.variantId());

            Inventory existingInventory = inventoryRepository.findWithBookDetailsByBookIdAndBookstoreIdAndCondition(
                    item.bookId(),
                    bookstoreId,
                    item.condition()
            ).orElse(null);

            if (existingInventory != null) {

                Optional<TiendanubeProductLink> existingInventoryLink =
                        productLinkRepository
                                .findByInventoryIdAndActiveTrue(existingInventory.getId());

                if (existingInventoryLink.isPresent()) {
                    throw new BusinessException(
                            "El inventario ya está vinculado con otra publicación de Tiendanube"
                    );
                }

                TiendanubeProductLinkResponse link =
                        productLinkService.linkExistingProduct(
                                existingInventory.getId(),
                                item.productId(),
                                item.variantId()
                        );

                existingInventory.setTiendanubePriceSyncEnabled(
                        Boolean.TRUE.equals(item.syncPrice())
                );

                inventoryRepository.save(existingInventory);

                productSyncService.syncMissingImage(existingInventory.getId());

                return new TiendanubeImportItemResultResponse(
                        item.productId(),
                        item.variantId(),
                        item.bookId(),
                        link.inventoryId(),
                        true,
                        TiendanubeImportAction.LINKED,
                        null
                );
            }

            TiendanubeImportCommand command = new TiendanubeImportCommand(
                    bookstoreId,
                    item.bookId(),
                    store.getStoreId(),
                    item.condition(),
                    item.stock(),
                    item.salePrice(),
                    item.syncPrice(),
                    item.editorialPriceSyncEnabled()
            );

            TiendanubeImportResultResponse result = importPersistenceService.importExistingBook(
                    command,
                    product,
                    variant
            );

            productSyncService.syncAfterImport(result.inventoryId());

            return new TiendanubeImportItemResultResponse(
                    item.productId(),
                    item.variantId(),
                    item.bookId(),
                    result.inventoryId(),
                    true,
                    TiendanubeImportAction.IMPORTED,
                    null
            );

        } catch (RuntimeException exception) {
            log.error("Error importando publicación Tiendanube. productId={}, variantId={}",
                    item.productId(), item.variantId(), exception);

            return new TiendanubeImportItemResultResponse(
                    item.productId(),
                    item.variantId(),
                    item.bookId(),
                    null,
                    false,
                    TiendanubeImportAction.FAILED,
                    exception.getMessage()
            );
        }
    }

    private List<TiendanubeImportPreviewItemResponse> buildPreviewItems(
            TiendanubeProductResponse product,
            PreviewContext context
    ) {
        if (product.variants() == null || product.variants().isEmpty()) {
            return List.of();
        }

        return product.variants().stream()
                .map(variant -> buildPreviewItem(product, variant, context))
                .toList();
    }

    private TiendanubeImportPreviewItemResponse buildPreviewItem(
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant,
            PreviewContext context
    ) {
        TiendanubeProductLink existingLink = context.linksByVariantId().get(variant.id());

        if (existingLink != null) {
            Inventory inventory = existingLink.getInventory();

            PreviewMatch match = new PreviewMatch(
                    TiendanubeImportMatchType.ALREADY_LINKED,
                    inventory.getBook().getId(),
                    inventory.getId(),
                    false,
                    false,
                    List.of(toCandidate(inventory.getBook()))
            );

            return buildItem(product, variant, match);
        }

        String isbn = TiendanubeProductUtils.resolveRemoteIsbn(variant);

        if (isbn != null) {
            Book book = context.booksByIsbn().get(isbn);

            if (book != null) {
                return buildBookMatchItem(product, variant, book, isbn, context);
            }
        }

        List<Book> textualCandidates = matchingService.findBookCandidates(
                product,
                context.books()
        );

        if (textualCandidates.isEmpty()) {
            PreviewMatch match = new PreviewMatch(
                    TiendanubeImportMatchType.BOOK_NOT_FOUND,
                    null,
                    null,
                    false,
                    true,
                    List.of()
            );

            return buildItem(product, variant, match);
        }

        if (textualCandidates.size() > 1) {
            PreviewMatch match = new PreviewMatch(
                    TiendanubeImportMatchType.MULTIPLE_MATCHES,
                    null,
                    null,
                    false,
                    true,
                    textualCandidates.stream().map(this::toCandidate).toList()
            );

            return buildItem(product, variant, match);
        }

        Book book = textualCandidates.getFirst();
        Inventory inventory = context.inventoriesByBookId().get(book.getId());

        PreviewMatch match = new PreviewMatch(
                TiendanubeImportMatchType.POSSIBLE_MATCH,
                book.getId(),
                inventory != null ? inventory.getId() : null,
                false,
                true,
                List.of(toCandidate(book))
        );

        return buildItem(product, variant, match);
    }

    private TiendanubeImportPreviewItemResponse buildBookMatchItem(
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant,
            Book book,
            String isbn,
            PreviewContext context
    ) {
        Inventory inventory = context.inventoriesByBookId().get(book.getId());

        if (inventory != null) {

            TiendanubeProductLink inventoryLink =
                    context.linksByInventoryId().get(inventory.getId());

            if (inventoryLink != null) {
                PreviewMatch match = new PreviewMatch(
                        TiendanubeImportMatchType.INVENTORY_ALREADY_LINKED,
                        book.getId(),
                        inventory.getId(),
                        false,
                        true,
                        List.of(toCandidate(book))
                );

                return buildItem(product, variant, match);
            }

            PreviewMatch match = new PreviewMatch(
                    TiendanubeImportMatchType.INVENTORY_EXISTS,
                    book.getId(),
                    inventory.getId(),
                    true,
                    false,
                    List.of(toCandidate(book))
            );

            return buildItem(product, variant, match);
        }

        TiendanubeImportMatchType matchType = isbn.equals(TiendanubeProductUtils.normalizeIdentifier(variant.barcode()))
                ? TiendanubeImportMatchType.EXACT_BARCODE
                : TiendanubeImportMatchType.EXACT_SKU;

        PreviewMatch match = new PreviewMatch(
                matchType,
                book.getId(),
                null,
                true,
                false,
                List.of(toCandidate(book))
        );

        return buildItem(product, variant, match);
    }

    private TiendanubeImportPreviewItemResponse buildItem(
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant,
            PreviewMatch match
    ) {
        return new TiendanubeImportPreviewItemResponse(
                product.id(),
                variant.id(),
                getProductName(product),
                getMainImageUrl(product),
                variant.sku(),
                variant.barcode(),
                variant.price(),
                variant.stock(),
                match.matchType(),
                match.bookId(),
                match.inventoryId(),
                match.selectedByDefault(),
                match.requiresReview(),
                match.candidates()
        );
    }

    private TiendanubeImportBookCandidateResponse toCandidate(Book book) {
        String authors = book.getAuthors() == null
                ? null
                : book.getAuthors().stream()
                .map(Author::getName)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);

        String publisher = book.getPublisher() == null ? null : book.getPublisher().getName();

        return new TiendanubeImportBookCandidateResponse(
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                authors,
                publisher
        );
    }

    private void validateVariantNotLinked(Long storeId, Long variantId) {
        if (productLinkRepository.findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(storeId, variantId).isPresent()) {
            throw new BusinessException("La variante ya está vinculada con un inventario");
        }
    }

    private TiendanubeImportPreviewResponse buildPreviewResponse(
            List<TiendanubeImportPreviewItemResponse> items,
            TiendanubeProductsPage page
    ) {
        long readyToImport = items.stream()
                .filter(item -> item.matchType() == TiendanubeImportMatchType.EXACT_BARCODE
                        || item.matchType() == TiendanubeImportMatchType.EXACT_SKU)
                .count();

        long readyToLink = items.stream()
                .filter(item ->
                        item.matchType() == TiendanubeImportMatchType.INVENTORY_EXISTS
                )
                .count();

        long requiresReview = items.stream()
                .filter(TiendanubeImportPreviewItemResponse::requiresReview)
                .count();

        long alreadyLinked = items.stream()
                .filter(item ->
                        item.matchType() == TiendanubeImportMatchType.ALREADY_LINKED
                )
                .count();

        return new TiendanubeImportPreviewResponse(
                page.total(),
                readyToImport,
                readyToLink,
                requiresReview,
                alreadyLinked,
                page.page(),
                page.size(),
                page.totalPages(),
                items
        );
    }

    private TiendanubeStore getActiveStore(Long bookstoreId) {
        return storeRepository.findByBookstoreIdAndActiveTrue(bookstoreId)
                .orElseThrow(() -> new BusinessException("La librería no tiene una cuenta Tiendanube vinculada"));
    }

    private String getProductName(TiendanubeProductResponse product) {
        return product.name() == null ? null : product.name().get("es");
    }

    private String getMainImageUrl(TiendanubeProductResponse product) {
        return product.images() == null || product.images().isEmpty() ? null : product.images().getFirst().src();
    }
}