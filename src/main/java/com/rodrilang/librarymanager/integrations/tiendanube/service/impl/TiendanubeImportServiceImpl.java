package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
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
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.repository.projection.InventoryTiendanubePreviewProjection;
import com.rodrilang.librarymanager.service.EditorialPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final IsbnService isbnService;
    private final EditorialPriceService editorialPriceService;
    private final BookstoreContext bookstoreContext;

    @Override
    public TiendanubeImportPreviewResponse preview(
            int page,
            int size
    ) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        TiendanubeStore store =
                getActiveStore(bookstoreId);

        TiendanubeProductsPage productsPage =
                client.fetchProductsPage(
                        store.getStoreId(),
                        page,
                        size
                );

        List<TiendanubeProductResponse> products = productsPage.products();

        List<Long> variantIds =
                products.stream()
                        .flatMap(product ->
                                product.variants() == null
                                        ? Stream.empty()
                                        : product.variants().stream()
                        )
                        .map(TiendanubeVariantResponse::id)
                        .toList();

        Map<Long, TiendanubeProductLink> linksByVariantId =
                productLinkRepository
                        .findAllByTiendanubeStoreIdAndTiendanubeVariantIdInAndActiveTrue(
                                store.getStoreId(),
                                variantIds
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                TiendanubeProductLink::getTiendanubeVariantId,
                                link -> link
                        ));

        Map<String, Book> booksByRemoteIsbn =
                loadBooksByRemoteIsbn(products, linksByVariantId);

        List<ProductPreviewData> previewData =
                products.stream()
                        .map(product ->
                                prepareProductPreview(
                                        product,
                                        linksByVariantId,
                                        booksByRemoteIsbn
                                )
                        )
                        .toList();

        List<Long> bookIds =
                previewData.stream()
                        .flatMap(data ->
                                data.bookIds().stream()
                        )
                        .distinct()
                        .toList();

        Map<Long, InventoryPreviewInfo> inventoryInfo =
                loadInventoryPreviewInfo(
                        bookstoreId,
                        bookIds
                );

        Map<Long, BigDecimal> editorialPrices = editorialPriceService.findCurrentPricesByBookIds(bookIds);

        PreviewContext context =
                new PreviewContext(
                        inventoryInfo,
                        editorialPrices
                );

        List<TiendanubeImportPreviewItemResponse> items =
                previewData.stream()
                        .flatMap(data ->
                                buildPreviewItems(
                                        data,
                                        context
                                ).stream()
                        )
                        .toList();

        return buildPreviewResponse(
                items,
                productsPage
        );
    }

    @Override
    public TiendanubeImportResultResponse importProduct(Long productId, Long variantId) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        TiendanubeStore store = getActiveStore(bookstoreId);

        validateVariantNotLinked(store.getStoreId(), variantId);

        TiendanubeProductResponse product = client.getProduct(store.getStoreId(), productId);
        TiendanubeVariantResponse variant = TiendanubeProductUtils.findVariant(product, variantId);
        String isbn = TiendanubeProductUtils.resolveRemoteIsbn(variant);

        if (isbn == null) {
            throw new BusinessException("La publicación no tiene ISBN. Debe asociarse manualmente a un libro existente.");
        }

        Book book = findBookByIsbn(isbn);

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
    public TiendanubeBulkImportResponse importProducts(TiendanubeBulkImportRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

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
            ProductPreviewData data,
            PreviewContext context
    ) {
        TiendanubeProductResponse product =
                data.product();

        if (product.variants() == null
                || product.variants().isEmpty()) {
            return List.of();
        }

        return product.variants()
                .stream()
                .map(variant ->
                        buildPreviewItem(
                                data,
                                variant,
                                context
                        )
                )
                .toList();
    }

    private TiendanubeImportPreviewItemResponse buildPreviewItem(
            ProductPreviewData data,
            TiendanubeVariantResponse variant,
            PreviewContext context
    ) {
        TiendanubeProductResponse product =
                data.product();

        TiendanubeProductLink existingLink =
                data.linksByVariantId()
                        .get(variant.id());

        if (existingLink != null) {
            Inventory inventory = existingLink.getInventory();

            Book book = inventory.getBook();

            log.info(
                    "Preview book source=LINK bookId={} authorsInitialized={}",
                    book.getId(),
                    Hibernate.isInitialized(book.getAuthors())
            );

            PreviewMatch match =
                    new PreviewMatch(
                            TiendanubeImportMatchType.ALREADY_LINKED,
                            book.getId(),
                            inventory.getId(),
                            false,
                            false,
                            List.of(
                                    toCandidate(
                                            book,
                                            context
                                    )
                            )
                    );

            return buildItem(
                    product,
                    variant,
                    match
            );
        }

        Book exactBook = data.booksByVariantId().get(variant.id());

        if (exactBook != null) {

            log.info(
                    "Preview book source=ISBN bookId={} authorsInitialized={}",
                    exactBook.getId(),
                    Hibernate.isInitialized(exactBook.getAuthors())
            );

            String isbn = TiendanubeProductUtils.resolveRemoteIsbn(variant);

            return buildBookMatchItem(
                    product,
                    variant,
                    exactBook,
                    isbn,
                    context
            );
        }

        List<Book> textualCandidates =
                data.candidatesByVariantId()
                        .getOrDefault(
                                variant.id(),
                                List.of()
                        );

        if (textualCandidates.isEmpty()) {
            return buildItem(
                    product,
                    variant,
                    new PreviewMatch(
                            TiendanubeImportMatchType.BOOK_NOT_FOUND,
                            null,
                            null,
                            false,
                            true,
                            List.of()
                    )
            );
        }

        if (textualCandidates.size() > 1) {

            for (Book candidate : textualCandidates) {
                log.info(
                        "Preview book source=TEXT bookId={} authorsInitialized={}",
                        candidate.getId(),
                        Hibernate.isInitialized(candidate.getAuthors())
                );
            }

            List<TiendanubeImportBookCandidateResponse> candidates =
                    textualCandidates.stream()
                            .map(book ->
                                    toCandidate(
                                            book,
                                            context
                                    )
                            )
                            .toList();

            return buildItem(
                    product,
                    variant,
                    new PreviewMatch(
                            TiendanubeImportMatchType.MULTIPLE_MATCHES,
                            null,
                            null,
                            false,
                            true,
                            candidates
                    )
            );
        }

        Book possibleBook = textualCandidates.getFirst();

        log.info(
                "Preview book source=TEXT_SINGLE bookId={} authorsInitialized={}",
                possibleBook.getId(),
                Hibernate.isInitialized(possibleBook.getAuthors())
        );

        return buildPossibleMatchItem(
                product,
                variant,
                possibleBook,
                context
        );
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

    private TiendanubeImportBookCandidateResponse toCandidate(
            Book book,
            PreviewContext context
    ) {

        log.info(
                "Building Tiendanube candidate. "
                        + "bookId={} title='{}' "
                        + "authorsInitialized={} "
                        + "publisherInitialized={}",
                book.getId(),
                book.getTitle(),
                Hibernate.isInitialized(book.getAuthors()),
                Hibernate.isInitialized(book.getPublisher())
        );

        String authors =
                book.getAuthors() == null
                        ? null
                        : book.getAuthors()
                        .stream()
                        .map(Author::getName)
                        .sorted()
                        .collect(
                                Collectors.joining(", ")
                        );

        String publisher =
                book.getPublisher() == null
                        ? null
                        : book.getPublisher().getName();

        InventoryPreviewInfo inventory =
                inventoryInfo(
                        book,
                        context
                );

        BigDecimal editorialPrice = context.editorialPrices().get(book.getId());

        return new TiendanubeImportBookCandidateResponse(
                book.getId(),
                book.getPreferredIsbn(),
                book.getTitle(),
                authors,
                publisher,
                editorialPrice,
                inventory != null
                        ? inventory.inventoryId()
                        : null,
                inventory != null
                        && inventory.linked()
        );
    }

    private void validateVariantNotLinked(Long storeId, Long variantId) {
        if (productLinkRepository.findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(storeId, variantId).isPresent()) {
            throw new BusinessException("La variante ya está vinculada con un inventario");
        }
    }

    private Map<String, Book> loadBooksByRemoteIsbn(
            List<TiendanubeProductResponse> products,
            Map<Long, TiendanubeProductLink> linksByVariantId
    ) {
        Map<String, ParsedIsbn> parsedByRemoteIsbn =
                collectParsedRemoteIsbns(
                        products,
                        linksByVariantId
                );

        if (parsedByRemoteIsbn.isEmpty()) {
            return Map.of();
        }

        Map<String, Book> booksByCanonicalIsbn = loadBooksByCanonicalIsbn(parsedByRemoteIsbn.values());

        return mapBooksByRemoteIsbn(
                parsedByRemoteIsbn,
                booksByCanonicalIsbn
        );
    }

    private Map<String, ParsedIsbn> collectParsedRemoteIsbns(
            List<TiendanubeProductResponse> products,
            Map<Long, TiendanubeProductLink> linksByVariantId
    ) {
        Map<String, ParsedIsbn> result = new HashMap<>();

        for (TiendanubeProductResponse product : products) {
            if (product.variants() == null) {
                continue;
            }

            for (TiendanubeVariantResponse variant : product.variants()) {
                if (linksByVariantId.containsKey(variant.id())) {
                    continue;
                }

                addParsedRemoteIsbn(variant, result);
            }
        }

        return result;
    }

    private void addParsedRemoteIsbn(
            TiendanubeVariantResponse variant,
            Map<String, ParsedIsbn> result
    ) {
        String remoteIsbn = TiendanubeProductUtils.resolveRemoteIsbn(variant);

        if (remoteIsbn == null || result.containsKey(remoteIsbn)) {
            return;
        }

        ParsedIsbn parsedIsbn = isbnService.parse(remoteIsbn);

        if (parsedIsbn.valid()) {
            result.put(remoteIsbn, parsedIsbn);
        }
    }

    private Map<String, Book> loadBooksByCanonicalIsbn(Collection<ParsedIsbn> parsedIsbns) {
        List<String> isbn13Values =
                parsedIsbns.stream()
                        .map(ParsedIsbn::isbn13)
                        .filter(this::hasText)
                        .distinct()
                        .toList();

        List<String> isbn10Values =
                parsedIsbns.stream()
                        .map(ParsedIsbn::isbn10)
                        .filter(this::hasText)
                        .distinct()
                        .toList();

        Map<String, Book> result = new HashMap<>();

        addBooksByIsbn13(isbn13Values, result);

        addBooksByIsbn10(isbn10Values, result);

        return result;
    }

    private void addBooksByIsbn13(
            List<String> isbn13Values,
            Map<String, Book> result
    ) {
        if (isbn13Values.isEmpty()) {
            return;
        }

        bookRepository.findByIsbn13InAndActiveTrue(isbn13Values)
                .forEach(book -> result.put(book.getIsbn13(), book));
    }

    private void addBooksByIsbn10(
            List<String> isbn10Values,
            Map<String, Book> result
    ) {
        if (isbn10Values.isEmpty()) {
            return;
        }

        bookRepository.findByIsbn10InAndActiveTrue(isbn10Values)
                .forEach(book ->
                        result.put(book.getIsbn10(), book));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, Book> mapBooksByRemoteIsbn(
            Map<String, ParsedIsbn> parsedByRemoteIsbn,
            Map<String, Book> booksByCanonicalIsbn
    ) {
        Map<String, Book> result = new HashMap<>();

        parsedByRemoteIsbn.forEach(
                (remoteIsbn, parsedIsbn) -> {
                    Book book = findBookByParsedIsbn(parsedIsbn, booksByCanonicalIsbn);

                    if (book != null) {
                        result.put(remoteIsbn, book);
                    }
                }
        );

        return result;
    }

    private Book findBookByParsedIsbn(ParsedIsbn parsedIsbn, Map<String, Book> booksByCanonicalIsbn) {
        if (parsedIsbn.isbn13() != null) {
            Book book = booksByCanonicalIsbn.get(parsedIsbn.isbn13());

            if (book != null) {
                return book;
            }
        }

        if (parsedIsbn.isbn10() != null) {
            return booksByCanonicalIsbn.get(parsedIsbn.isbn10());
        }

        return null;
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

    private Book findBookByIsbn(String value) {
        ParsedIsbn parsedIsbn = isbnService.parse(value);

        if (!parsedIsbn.valid()) {
            throw new BusinessException("La publicación no tiene un ISBN válido.");
        }

        if (parsedIsbn.isbn13() != null) {
            Optional<Book> byIsbn13 = bookRepository.findByIsbn13WithDetails(parsedIsbn.isbn13());

            if (byIsbn13.isPresent()) {
                return byIsbn13.get();
            }
        }

        if (parsedIsbn.isbn10() != null) {
            Optional<Book> byIsbn10 = bookRepository.findByIsbn10WithDetails(parsedIsbn.isbn10());

            if (byIsbn10.isPresent()) {
                return byIsbn10.get();
            }
        }

        throw new BusinessException(
                "No existe un libro en el catálogo con ISBN " + value
        );
    }

    private TiendanubeImportPreviewItemResponse buildBookMatchItem(
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant,
            Book book,
            String isbn,
            PreviewContext context
    ) {
        InventoryPreviewInfo inventory =
                inventoryInfo(
                        book,
                        context
                );

        TiendanubeImportBookCandidateResponse candidate =
                toCandidate(
                        book,
                        context
                );

        if (inventory != null) {

            if (inventory.linked()) {
                return buildItem(
                        product,
                        variant,
                        new PreviewMatch(
                                TiendanubeImportMatchType.INVENTORY_ALREADY_LINKED,
                                book.getId(),
                                inventory.inventoryId(),
                                false,
                                true,
                                List.of(candidate)
                        )
                );
            }

            return buildItem(
                    product,
                    variant,
                    new PreviewMatch(
                            TiendanubeImportMatchType.INVENTORY_EXISTS,
                            book.getId(),
                            inventory.inventoryId(),
                            true,
                            false,
                            List.of(candidate)
                    )
            );
        }

        TiendanubeImportMatchType matchType =
                isbn != null
                        && isbn.equals(
                        TiendanubeProductUtils
                                .normalizeIdentifier(
                                        variant.barcode()
                                )
                )
                        ? TiendanubeImportMatchType.EXACT_BARCODE
                        : TiendanubeImportMatchType.EXACT_SKU;

        return buildItem(
                product,
                variant,
                new PreviewMatch(
                        matchType,
                        book.getId(),
                        null,
                        true,
                        false,
                        List.of(candidate)
                )
        );
    }

    private TiendanubeImportPreviewItemResponse buildPossibleMatchItem(
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant,
            Book book,
            PreviewContext context
    ) {
        InventoryPreviewInfo inventory =
                inventoryInfo(
                        book,
                        context
                );

        TiendanubeImportBookCandidateResponse candidate =
                toCandidate(
                        book,
                        context
                );

        if (inventory != null
                && inventory.linked()) {

            return buildItem(
                    product,
                    variant,
                    new PreviewMatch(
                            TiendanubeImportMatchType.INVENTORY_ALREADY_LINKED,
                            book.getId(),
                            inventory.inventoryId(),
                            false,
                            true,
                            List.of(candidate)
                    )
            );
        }

        return buildItem(
                product,
                variant,
                new PreviewMatch(
                        TiendanubeImportMatchType.POSSIBLE_MATCH,
                        book.getId(),
                        inventory != null
                                ? inventory.inventoryId()
                                : null,
                        false,
                        true,
                        List.of(candidate)
                )
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

    private Map<Long, InventoryPreviewInfo> loadInventoryPreviewInfo(
            Long bookstoreId,
            Collection<Long> bookIds
    ) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Map.of();
        }

        return inventoryRepository
                .findTiendanubePreviewByBookIds(
                        bookstoreId,
                        bookIds
                )
                .stream()
                .collect(
                        Collectors.toMap(
                                InventoryTiendanubePreviewProjection::getBookId,
                                projection ->
                                        new InventoryPreviewInfo(
                                                projection.getInventoryId(),
                                                Boolean.TRUE.equals(
                                                        projection.getLinked()
                                                )
                                        ),
                                (first, second) -> first
                        )
                );
    }

    private ProductPreviewData prepareProductPreview(
            TiendanubeProductResponse product,
            Map<Long, TiendanubeProductLink> allLinksByVariantId,
            Map<String, Book> booksByRemoteIsbn
    ) {
        Map<Long, TiendanubeProductLink> linksByVariantId = new HashMap<>();

        Map<Long, Book> booksByVariantId = new HashMap<>();

        Map<Long, List<Book>> candidatesByVariantId = new HashMap<>();

        if (product.variants() == null) {
            return new ProductPreviewData(
                    product,
                    linksByVariantId,
                    booksByVariantId,
                    candidatesByVariantId
            );
        }

        List<Book> textualCandidates = null;

        for (TiendanubeVariantResponse variant : product.variants()) {

            TiendanubeProductLink existingLink = allLinksByVariantId.get(variant.id());

            if (existingLink != null) {
                linksByVariantId.put(variant.id(), existingLink);

                booksByVariantId.put(variant.id(), existingLink.getInventory().getBook());

                continue;
            }

            String isbn = TiendanubeProductUtils.resolveRemoteIsbn(variant);

            if (isbn != null) {
                Book book = booksByRemoteIsbn.get(isbn);

                if (book != null) {
                    booksByVariantId.put(variant.id(), book);

                    continue;
                }
            }

            if (textualCandidates == null) {
                textualCandidates = matchingService.findBookCandidates(product);
            }

            candidatesByVariantId.put(
                    variant.id(),
                    textualCandidates
            );
        }

        return new ProductPreviewData(
                product,
                linksByVariantId,
                booksByVariantId,
                candidatesByVariantId
        );
    }

    private InventoryPreviewInfo inventoryInfo(
            Book book,
            PreviewContext context
    ) {
        return context.inventoryInfo()
                .get(book.getId());
    }

    private record PreviewContext(
            Map<Long, InventoryPreviewInfo> inventoryInfo,
            Map<Long, BigDecimal> editorialPrices
    ) {
    }

    private record InventoryPreviewInfo(
            Long inventoryId,
            boolean linked
    ) {
    }

    private record ProductPreviewData(
            TiendanubeProductResponse product,
            Map<Long, TiendanubeProductLink> linksByVariantId,
            Map<Long, Book> booksByVariantId,
            Map<Long, List<Book>> candidatesByVariantId
    ) {
        List<Long> bookIds() {
            return Stream.concat(
                            booksByVariantId.values().stream(),
                            candidatesByVariantId.values()
                                    .stream()
                                    .flatMap(List::stream)
                    )
                    .map(Book::getId)
                    .distinct()
                    .toList();
        }
    }
}