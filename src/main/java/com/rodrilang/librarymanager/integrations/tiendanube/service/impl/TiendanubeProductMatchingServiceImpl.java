package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.internal.MatchResult;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.internal.RemoteInventoryMatch;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.InventoryMatchCandidateResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRemoteProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRemoteVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeMatchType;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductMatchingService;
import com.rodrilang.librarymanager.integrations.tiendanube.util.TiendanubeProductUtils;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TiendanubeProductMatchingServiceImpl implements TiendanubeProductMatchingService {

    private final InventoryRepository inventoryRepository;
    private final BookRepository bookRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final IsbnService isbnService;

    @Override
    public TiendanubeRemoteProductResponse analyze(Long bookstoreId, Long storeId, TiendanubeProductResponse product) {
        List<TiendanubeRemoteVariantResponse> variants = product.variants() == null
                ? List.of()
                : product.variants().stream()
                .map(variant -> analyzeVariant(bookstoreId, storeId, product, variant))
                .toList();

        return new TiendanubeRemoteProductResponse(
                product.id(),
                getProductName(product),
                getMainImageUrl(product),
                product.published(),
                variants
        );
    }

    @Override
    public RemoteInventoryMatch findRemoteMatch(
            Inventory inventory,
            List<TiendanubeProductResponse> products
    ) {
        List<RemoteInventoryMatch> exactMatches = findExactRemoteMatches(products, inventory);

        if (exactMatches.size() == 1) {
            return exactMatches.getFirst();
        }

        if (exactMatches.size() > 1) {
            return new RemoteInventoryMatch(null, null, TiendanubeMatchType.MULTIPLE_MATCHES);
        }

        List<RemoteInventoryMatch> textualMatches = findTextualRemoteMatches(products, inventory);

        if (textualMatches.size() == 1) {
            return textualMatches.getFirst();
        }

        if (textualMatches.size() > 1) {
            return new RemoteInventoryMatch(null, null, TiendanubeMatchType.MULTIPLE_MATCHES);
        }

        return null;
    }

    @Override
    public List<Book> findBookCandidates(
            TiendanubeProductResponse product
    ) {
        String remoteName =
                TextNormalizer.normalizeForMatch(
                        getProductName(product)
                );

        if (remoteName.isBlank()) {
            return List.of();
        }

        List<Long> candidateIds =
                bookRepository.findTiendanubeCandidateIds(
                        remoteName,
                        50
                );

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> positionById =
                new HashMap<>();

        for (int i = 0; i < candidateIds.size(); i++) {
            positionById.put(
                    candidateIds.get(i),
                    i
            );
        }

        List<Book> candidates =
                bookRepository
                        .findAllWithDetailsByIdIn(
                                candidateIds
                        )
                        .stream()
                        .sorted(
                                Comparator.comparingInt(
                                        book ->
                                                positionById.getOrDefault(
                                                        book.getId(),
                                                        Integer.MAX_VALUE
                                                )
                                )
                        )
                        .toList();

        List<Book> titleCandidates =
                candidates.stream()
                        .filter(book ->
                                matchesTitle(
                                        remoteName,
                                        book
                                )
                        )
                        .toList();

        if (titleCandidates.size() <= 1) {
            return titleCandidates;
        }

        List<Book> authorCandidates =
                titleCandidates.stream()
                        .filter(book ->
                                matchesAuthor(
                                        remoteName,
                                        book
                                )
                        )
                        .toList();

        return authorCandidates.isEmpty()
                ? titleCandidates
                : authorCandidates;
    }

    private TiendanubeRemoteVariantResponse analyzeVariant(
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

    private MatchResult findMatch(
            Long bookstoreId,
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant
    ) {
        MatchResult barcodeMatch = findInventoryMatchByIsbn(
                bookstoreId,
                variant.barcode(),
                TiendanubeMatchType.EXACT_BARCODE
        );

        if (barcodeMatch != null) {
            return barcodeMatch;
        }

        MatchResult skuMatch = findInventoryMatchByIsbn(
                bookstoreId,
                variant.sku(),
                TiendanubeMatchType.EXACT_SKU
        );

        if (skuMatch != null) {
            return skuMatch;
        }

        return findTextualMatch(bookstoreId, product);
    }

    private MatchResult findInventoryMatchByIsbn(
            Long bookstoreId,
            String value,
            TiendanubeMatchType matchType
    ) {
        String normalized = TiendanubeProductUtils.normalizeIdentifier(value);

        if (normalized == null) {
            return null;
        }

        ParsedIsbn parsedIsbn = isbnService.parse(normalized);

        if (!parsedIsbn.valid()) {
            return null;
        }

        List<Inventory> candidates = inventoryRepository.findAllByBookstoreAndIsbn(
                bookstoreId,
                parsedIsbn.isbn13(),
                parsedIsbn.isbn10()
        );

        if (candidates.isEmpty()) {
            return null;
        }

        return createMatchResult(matchType, candidates);
    }

    private MatchResult findTextualMatch(Long bookstoreId, TiendanubeProductResponse product) {
        String remoteName = TextNormalizer.normalizeForMatch(getProductName(product));

        if (remoteName.isBlank()) {
            return new MatchResult(TiendanubeMatchType.NOT_FOUND, List.of());
        }

        List<Inventory> inventories = inventoryRepository.findAllByBookstoreId(bookstoreId);

        List<Inventory> titleCandidates = inventories.stream()
                .filter(inventory -> matchesTitle(remoteName, inventory.getBook()))
                .toList();

        if (titleCandidates.isEmpty()) {
            return new MatchResult(TiendanubeMatchType.NOT_FOUND, List.of());
        }

        List<Inventory> titleAndAuthorCandidates = titleCandidates.stream()
                .filter(inventory -> matchesAuthor(remoteName, inventory.getBook()))
                .toList();

        if (!titleAndAuthorCandidates.isEmpty()) {
            return createPossibleMatchResult(titleAndAuthorCandidates);
        }

        return createPossibleMatchResult(titleCandidates);
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

    private MatchResult createPossibleMatchResult(List<Inventory> inventories) {
        List<InventoryMatchCandidateResponse> candidates = inventories.stream()
                .map(this::toCandidate)
                .toList();

        if (candidates.size() > 1) {
            return new MatchResult(TiendanubeMatchType.MULTIPLE_MATCHES, candidates);
        }

        return new MatchResult(TiendanubeMatchType.POSSIBLE_MATCH, candidates);
    }

    private InventoryMatchCandidateResponse toCandidate(Inventory inventory) {
        Book book = inventory.getBook();

        String authors = book.getAuthors() == null
                ? null
                : book.getAuthors().stream()
                .map(Author::getName)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);

        String publisher = book.getPublisher() == null ? null : book.getPublisher().getName();

        return new InventoryMatchCandidateResponse(
                inventory.getId(),
                book.getId(),
                book.getPreferredIsbn(),
                book.getTitle(),
                authors,
                publisher,
                inventory.getCondition(),
                inventory.getStock()
        );
    }

    private boolean matchesTitle(String remoteName, Book book) {
        String title = TextNormalizer.normalizeForMatch(book.getTitle());

        if (title.isBlank()) {
            return false;
        }

        return remoteName.equals(title) || remoteName.startsWith(title + " ");
    }

    private boolean matchesAuthor(String remoteName, Book book) {
        if (book.getAuthors() == null || book.getAuthors().isEmpty()) {
            return false;
        }

        return book.getAuthors().stream()
                .anyMatch(author -> containsAllTokens(
                        remoteName,
                        TextNormalizer.normalizeForMatch(author.getName())
                ));
    }

    private boolean containsAllTokens(String text, String candidate) {
        if (candidate.isBlank()) {
            return false;
        }

        List<String> textTokens = List.of(text.split(" "));

        return Stream.of(candidate.split(" "))
                .filter(token -> token.length() > 1)
                .allMatch(textTokens::contains);
    }

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

        return product.images().getFirst().src();
    }

    private List<RemoteInventoryMatch> findExactRemoteMatches(
            List<TiendanubeProductResponse> products,
            Inventory inventory
    ) {
        String isbn = TiendanubeProductUtils.normalizeIdentifier(inventory.getBook().getPreferredIsbn());

        if (isbn == null) {
            return List.of();
        }

        return products.stream()
                .flatMap(product -> product.variants() == null
                        ? Stream.empty()
                        : product.variants().stream()
                        .filter(variant -> matchesIsbn(variant, isbn))
                        .map(variant -> new RemoteInventoryMatch(
                                product.id(),
                                variant.id(),
                                resolveExactMatchType(variant, isbn)
                        )))
                .toList();
    }

    private TiendanubeMatchType resolveExactMatchType(TiendanubeVariantResponse variant, String isbn) {
        if (isbn.equals(TiendanubeProductUtils.normalizeIdentifier(variant.barcode()))) {
            return TiendanubeMatchType.EXACT_BARCODE;
        }

        return TiendanubeMatchType.EXACT_SKU;
    }

    private boolean matchesIsbn(TiendanubeVariantResponse variant, String isbn) {
        String barcode = TiendanubeProductUtils.normalizeIdentifier(variant.barcode());
        String sku = TiendanubeProductUtils.normalizeIdentifier(variant.sku());

        return isbn.equals(barcode) || isbn.equals(sku);
    }

    private List<RemoteInventoryMatch> findTextualRemoteMatches(
            List<TiendanubeProductResponse> products,
            Inventory inventory
    ) {
        Book book = inventory.getBook();

        return products.stream()
                .filter(product -> matchesRemoteProductText(product, book))
                .flatMap(product -> product.variants() == null
                        ? Stream.empty()
                        : product.variants().stream()
                        .map(variant -> new RemoteInventoryMatch(
                                product.id(),
                                variant.id(),
                                TiendanubeMatchType.POSSIBLE_MATCH
                        )))
                .toList();
    }

    private boolean matchesRemoteProductText(TiendanubeProductResponse product, Book book) {
        String remoteName = TextNormalizer.normalizeForMatch(getProductName(product));
        String localTitle = TextNormalizer.normalizeForMatch(book.getTitle());

        if (remoteName.isBlank() || localTitle.isBlank()) {
            return false;
        }

        boolean titleMatches = remoteName.equals(localTitle) || remoteName.startsWith(localTitle + " ");

        if (!titleMatches) {
            return false;
        }

        if (remoteName.equals(localTitle)) {
            return true;
        }

        return matchesAuthor(remoteName, book);
    }
}