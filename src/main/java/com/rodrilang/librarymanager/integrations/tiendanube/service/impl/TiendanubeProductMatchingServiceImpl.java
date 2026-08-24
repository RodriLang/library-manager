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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TiendanubeProductMatchingServiceImpl implements TiendanubeProductMatchingService {

    private static final int TEXT_CANDIDATE_LIMIT = 15;
    private static final int RETURNED_CANDIDATE_LIMIT = 5;

    private static final double MIN_TITLE_SCORE = 0.45;

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
    public List<Book> findBookCandidates(TiendanubeProductResponse product) {
        String productName = getProductName(product);

        String remoteSearchName = TextNormalizer.normalizeForSearch(productName);
        String remoteMatchName = TextNormalizer.normalizeForMatch(productName);

        if (remoteSearchName.isBlank()) {
            return List.of();
        }

        String fullTextQuery = buildCandidateFullTextQuery(remoteSearchName);

        if (fullTextQuery.isBlank()) {
            return List.of();
        }

        List<Long> candidateIds = bookRepository.findTiendanubeCandidateIds(
                fullTextQuery,
                TEXT_CANDIDATE_LIMIT
        );

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        return bookRepository
                .findAllWithDetailsByIdIn(candidateIds)
                .stream()
                .map(book -> scoreCandidate(remoteSearchName, remoteMatchName, book))
                .filter(candidate -> candidate.titleScore() >= MIN_TITLE_SCORE)
                .sorted(Comparator.comparingDouble(BookMatchScore::score).reversed())
                .limit(RETURNED_CANDIDATE_LIMIT)
                .map(BookMatchScore::book)
                .toList();
    }

    private String buildCandidateFullTextQuery(String normalizedName) {
        return Stream.of(normalizedName.split("\\s+"))
                .filter(token -> token.length() > 2)
                .map(token -> token + ":*")
                .collect(Collectors.joining(" | "));
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
        List<Book> bookCandidates = findBookCandidates(product);

        if (bookCandidates.isEmpty()) {
            return new MatchResult(TiendanubeMatchType.NOT_FOUND, List.of());
        }

        List<Long> bookIds = bookCandidates.stream()
                .map(Book::getId)
                .toList();

        List<Inventory> inventories = inventoryRepository.findAllByBookstoreIdAndBookIdInAndActiveTrue(
                bookstoreId,
                bookIds
        );

        if (inventories.isEmpty()) {
            return new MatchResult(TiendanubeMatchType.NOT_FOUND, List.of());
        }

        return createPossibleMatchResult(inventories);
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

    private double authorScore(String remoteName, Book book) {
        if (book.getAuthors() == null || book.getAuthors().isEmpty()) {
            return 0;
        }

        return book.getAuthors()
                .stream()
                .map(Author::getName)
                .map(TextNormalizer::normalizeForMatch)
                .mapToDouble(author -> titleScore(remoteName, author))
                .max()
                .orElse(0);
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
        String productName = getProductName(product);
        String remoteSearchName = TextNormalizer.normalizeForSearch(productName);

        if (remoteSearchName.isBlank()) {
            return false;
        }

        return titleScore(remoteSearchName, book.getTitleSearch()) >= MIN_TITLE_SCORE;
    }

    private BookMatchScore scoreCandidate(
            String remoteSearchName,
            String remoteMatchName,
            Book book
    ) {
        double titleScore = titleScore(remoteSearchName, book.getTitleSearch());

        double authorScore = titleScore >= MIN_TITLE_SCORE
                ? authorScore(remoteMatchName, book)
                : 0;

        double score =
                titleScore * 0.85
                        + authorScore * 0.15;

        return new BookMatchScore(
                book,
                titleScore,
                score
        );
    }

    private double titleScore(String remoteName, String title) {
        if (remoteName == null || remoteName.isBlank() || title == null || title.isBlank()) {
            return 0;
        }

        List<String> remoteTokens = significantTokens(remoteName);
        List<String> titleTokens = significantTokens(title);

        if (remoteTokens.isEmpty() || titleTokens.isEmpty()) {
            return 0;
        }

        double total = 0;

        for (String titleToken : titleTokens) {
            double bestMatch = remoteTokens.stream()
                    .mapToDouble(remoteToken -> tokenSimilarity(titleToken, remoteToken))
                    .max()
                    .orElse(0);

            total += bestMatch;
        }

        double coverage = total / titleTokens.size();

        if (titleTokens.size() == 1 && remoteTokens.size() >= 3) {
            coverage *= 0.65;
        }

        return coverage;
    }

    private List<String> significantTokens(String value) {
        return Stream.of(value.split("\\s+"))
                .filter(token -> token.length() > 1)
                .toList();
    }

    private double tokenSimilarity(String first, String second) {
        if (first.equals(second)) {
            return 1.0;
        }

        int minLength = Math.min(first.length(), second.length());
        int maxLength = Math.max(first.length(), second.length());

        if (minLength >= 5 && (first.startsWith(second) || second.startsWith(first))) {
            return (double) minLength / maxLength;
        }

        return 0;
    }

    private record BookMatchScore(
            Book book,
            double titleScore,
            double score
    ) {
    }
}